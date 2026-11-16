package br.com.ecommerce.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import br.com.ecommerce.pagamentos.banco.BancoClient;
import br.com.ecommerce.pagamentos.banco.BancoIndisponivelException;
import br.com.ecommerce.pagamentos.banco.BancoRecusouException;
import br.com.ecommerce.pagamentos.evento.KafkaSaude;
import br.com.ecommerce.pagamentos.evento.PagamentosListener;
import br.com.ecommerce.pagamentos.security.UsuarioLogado;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * O teste faz o papel do Kafka (entrega a mensagem ao listener) e do banco do cartão (BancoClient
 * simulado: aprova, recusa, falha, demora). O que seria publicado é conferido no KafkaTemplate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PagamentosTests {

	@Autowired
	MockMvc mvc;

	@MockitoBean
	BancoClient banco;

	@MockitoBean
	KafkaTemplate<String, String> kafka;

	@Autowired
	PagamentosListener listener;

	@Autowired
	CircuitBreaker circuito;

	@MockitoBean
	KafkaSaude kafkaSaude;

	final UUID cliente = UUID.randomUUID();

	@BeforeEach
	void kafkaNoArECircuitoFechado() {
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
		circuito.reset();
		when(kafkaSaude.noAr()).thenReturn(true);
	}

	// ---------- cobrança ----------

	@Test
	void pixAprovadoGravaEPublicaPagamentoAprovado() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA1");

		estoqueReservado(pedido, "pix", null);

		String evento = publicado(pedido);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("pagamento.aprovado");
		assertThat(JsonPath.<String>read(evento, "$.correlationId")).isEqualTo("corr-teste");
		assertThat(JsonPath.<String>read(evento, "$.dados.codigoTransacao")).isEqualTo("TX-AAA1");
		assertThat(JsonPath.<String>read(evento, "$.dados.clienteEmail")).isEqualTo("cliente@loja.com");
		assertThat(evento).contains("\"valor\":60.00");

		comoDono(pedido)
			.andExpect(jsonPath("$.status").value("APROVADO"))
			.andExpect(jsonPath("$.tentativas").value(1))
			.andExpect(jsonPath("$.codigoTransacao").value("TX-AAA1"))
			.andExpect(jsonPath("$.forma").value("pix"));
		verify(banco).cobrar(eq(UUID.fromString(pedido)), eq(new BigDecimal("60.00")), eq("pix"), isNull());
	}

	@Test
	void mesmoEventoDuasVezesNaoCobraDeNovoERepublicaOResultado() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA2");

		estoqueReservado(pedido, "cartao", "4111");
		estoqueReservado(pedido, "cartao", "4111"); // pelo menos uma vez: chegou de novo

		verify(banco, times(1)).cobrar(any(), any(), any(), any());
		assertThat(publicados(pedido)).hasSize(2)
			.allSatisfy(e -> assertThat(JsonPath.<String>read(e, "$.tipo")).isEqualTo("pagamento.aprovado"));
	}

	@Test
	void cartaoRecusadoNaoRepeteEPublicaRecusadoComOMotivo() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenThrow(new BancoRecusouException("saldo insuficiente"));

		estoqueReservado(pedido, "cartao", "0002");

		// 402 é uma resposta: insistir não muda o "não"
		verify(banco, times(1)).cobrar(any(), any(), any(), any());
		String evento = publicado(pedido);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("pagamento.recusado");
		assertThat(JsonPath.<String>read(evento, "$.dados.motivo")).isEqualTo("saldo insuficiente");
		comoDono(pedido).andExpect(jsonPath("$.status").value("RECUSADO"))
			.andExpect(jsonPath("$.cartaoFinal").value("0002"));
		assertThat(circuito.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
	}

	@Test
	void falhaPassageiraORetryResolveNaTerceiraTentativa() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any()))
			.thenThrow(indisponivel("HTTP 503"), indisponivel("HTTP 503"))
			.thenReturn("TX-AAA3");

		estoqueReservado(pedido, "pix", null);

		verify(banco, times(3)).cobrar(any(), any(), any(), any());
		assertThat(JsonPath.<String>read(publicado(pedido), "$.tipo")).isEqualTo("pagamento.aprovado");
		comoDono(pedido).andExpect(jsonPath("$.status").value("APROVADO")).andExpect(jsonPath("$.tentativas").value(3));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void bancoForaEsgotaAsTentativasEPublicaRecusado(CapturedOutput saida) throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenThrow(indisponivel("HTTP 503"));

		estoqueReservado(pedido, "pix", null);

		verify(banco, times(3)).cobrar(any(), any(), any(), any());
		// 503 quer dizer "não processei": não há o que estornar
		verify(banco, never()).estornar(any(), any());
		String evento = publicado(pedido);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("pagamento.recusado");
		assertThat(JsonPath.<String>read(evento, "$.dados.motivo")).isEqualTo("banco indisponível (HTTP 503)");
		comoDono(pedido).andExpect(jsonPath("$.status").value("ERRO")).andExpect(jsonPath("$.tentativas").value(3));
		// Cada tentativa vai para o log, com o correlationId
		assertThat(saida.getOut()).contains("[corr-teste]").contains("tentativa 1/3 falhou: HTTP 503")
			.contains("tentativa 3/3 falhou: HTTP 503");
	}

	@Test
	void timeoutPedeEstornoDeSegurancaPorqueOBancoPodeTerCobrado() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any()))
			.thenThrow(new BancoIndisponivelException("sem resposta em 2000 ms (timeout)", true, null));
		when(banco.estornar(any(), any())).thenReturn(true);

		estoqueReservado(pedido, "pix", null);

		verify(banco).estornar(isNull(), eq(UUID.fromString(pedido)));
		assertThat(JsonPath.<String>read(publicado(pedido), "$.dados.motivo"))
			.isEqualTo("banco indisponível (sem resposta em 2000 ms (timeout))");
		comoDono(pedido).andExpect(jsonPath("$.status").value("ERRO"));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void bancoForaAbreOCircuitoEAsProximasCobrancasNemChamamOBanco(CapturedOutput saida) throws Exception {
		when(banco.cobrar(any(), any(), any(), any())).thenThrow(indisponivel("HTTP 503"));
		String primeiro = UUID.randomUUID().toString();
		String segundo = UUID.randomUUID().toString();
		String terceiro = UUID.randomUUID().toString();

		estoqueReservado(primeiro, "pix", null);  // 3 falhas
		estoqueReservado(segundo, "pix", null);   // mais 2: 5 chamadas, 100% de falha -> abre
		estoqueReservado(terceiro, "pix", null);  // circuito aberto: nem chama

		verify(banco, times(5)).cobrar(any(), any(), any(), any());
		assertThat(saida.getOut()).contains("CIRCUITO ABERTO");
		comoDono(segundo).andExpect(jsonPath("$.tentativas").value(2));
		comoDono(terceiro)
			.andExpect(jsonPath("$.status").value("ERRO"))
			.andExpect(jsonPath("$.tentativas").value(0))
			.andExpect(jsonPath("$.motivo").value("banco indisponível (circuito aberto)"));
		mvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("degradado"))
			.andExpect(jsonPath("$.dependencias.circuitoBancoExterno").value("aberto"));

		// Passado o tempo de espera, o meio-aberto deixa uma chamada testar; deu certo, fecha
		circuito.transitionToHalfOpenState();
		mvc.perform(get("/health")).andExpect(jsonPath("$.dependencias.circuitoBancoExterno").value("meio-aberto"));
		doReturn("TX-VOLTOU").when(banco).cobrar(any(), any(), any(), any());
		estoqueReservado(UUID.randomUUID().toString(), "pix", null);
		mvc.perform(get("/health")).andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.dependencias.circuitoBancoExterno").value("fechado"));
	}

	// ---------- cancelamento ----------

	@Test
	void canceladoAntesDaCobrancaBloqueiaACobrancaFutura() throws Exception {
		String pedido = UUID.randomUUID().toString();

		pedidoCancelado(pedido);
		estoqueReservado(pedido, "pix", null); // chegou depois do cancelamento

		verify(banco, never()).cobrar(any(), any(), any(), any());
		verify(kafka, never()).send(anyString(), anyString(), anyString());
		comoDono(pedido).andExpect(jsonPath("$.status").value("CANCELADO"));
	}

	@Test
	void canceladoDepoisDeAprovadoEstornaEPublicaPagamentoEstornado() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA4");
		when(banco.estornar(any(), any())).thenReturn(true);
		estoqueReservado(pedido, "pix", null);

		pedidoCancelado(pedido);
		pedidoCancelado(pedido); // repetido: não estorna de novo

		verify(banco, times(1)).estornar("TX-AAA4", UUID.fromString(pedido));
		String evento = publicado(pedido);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("pagamento.estornado");
		assertThat(JsonPath.<String>read(evento, "$.dados.codigoTransacao")).isEqualTo("TX-AAA4");
		comoDono(pedido).andExpect(jsonPath("$.status").value("ESTORNADO"))
			.andExpect(jsonPath("$.codigoTransacao").value("TX-AAA4"));
	}

	@Test
	void canceladoDepoisDeRecusadoNaoTemNadaAEstornar() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenThrow(new BancoRecusouException("saldo insuficiente"));
		estoqueReservado(pedido, "cartao", "0002");

		pedidoCancelado(pedido);

		verify(banco, never()).estornar(any(), any());
		comoDono(pedido).andExpect(jsonPath("$.status").value("RECUSADO"));
	}

	@Test
	void estornoQueFalhaSobeParaOKafkaEntregarDeNovo() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA5");
		when(banco.estornar(any(), any())).thenThrow(indisponivel("HTTP 503"));
		estoqueReservado(pedido, "pix", null);

		org.assertj.core.api.Assertions.assertThatThrownBy(() -> pedidoCancelado(pedido))
			.isInstanceOf(BancoIndisponivelException.class);

		// A compensação não some: continua APROVADO e o Kafka entrega o pedido.cancelado de novo
		comoDono(pedido).andExpect(jsonPath("$.status").value("APROVADO"));
	}

	// ---------- rotas ----------

	@Test
	void donoVeOProprioPagamentoEOutroClienteRecebe404() throws Exception {
		String pedido = UUID.randomUUID().toString();
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA6");
		estoqueReservado(pedido, "pix", null);

		comoDono(pedido).andExpect(jsonPath("$.pedidoId").value(pedido));
		mvc.perform(get("/pagamentos/" + pedido).header(UsuarioLogado.ID, UUID.randomUUID().toString())
				.header(UsuarioLogado.PERFIL, "cliente"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Pagamento não encontrado."));
		mvc.perform(get("/pagamentos/" + pedido).header(UsuarioLogado.ID, UUID.randomUUID().toString())
				.header(UsuarioLogado.PERFIL, "admin"))
			.andExpect(status().isOk());
		mvc.perform(get("/pagamentos/nao-eh-uuid").header(UsuarioLogado.ID, cliente.toString()))
			.andExpect(status().isNotFound());
	}

	@Test
	void listarTodosSoParaAdmin() throws Exception {
		when(banco.cobrar(any(), any(), any(), any())).thenReturn("TX-AAA7");
		estoqueReservado(UUID.randomUUID().toString(), "pix", null);

		mvc.perform(get("/pagamentos").header(UsuarioLogado.ID, cliente.toString())
				.header(UsuarioLogado.PERFIL, "cliente"))
			.andExpect(status().isForbidden());
		mvc.perform(get("/pagamentos").header(UsuarioLogado.ID, UUID.randomUUID().toString())
				.header(UsuarioLogado.PERFIL, "admin"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
		mvc.perform(get("/pagamentos")).andExpect(status().isUnauthorized());
	}

	@Test
	void healthMostraOBancoEOCircuito() throws Exception {
		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servico").value("pagamentos"))
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.dependencias.banco").value("ok"))
			.andExpect(jsonPath("$.dependencias.kafka").value("ok"))
			.andExpect(jsonPath("$.dependencias.circuitoBancoExterno").value("fechado"));
	}

	@Test
	void healthSemKafkaEstaForaDoAr() throws Exception {
		when(kafkaSaude.noAr()).thenReturn(false);

		mvc.perform(get("/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("fora do ar"))
			.andExpect(jsonPath("$.dependencias.kafka").value("fora"));
	}

	// ---------- apoio ----------

	private void estoqueReservado(String pedidoId, String forma, String cartaoFinal) {
		String cartao = cartaoFinal == null ? "" : ",\"cartaoFinal\":\"" + cartaoFinal + "\"";
		listener.receber(mensagem("estoque.reservado", pedidoId, """
				{"usuarioId":"%s","clienteEmail":"cliente@loja.com","valorTotal":60.00,"formaPagamento":"%s"%s}"""
			.formatted(cliente, forma, cartao)));
	}

	private void pedidoCancelado(String pedidoId) {
		listener.receber(mensagem("pedido.cancelado", pedidoId, """
				{"usuarioId":"%s","clienteEmail":"cliente@loja.com","valorTotal":60.00,"formaPagamento":"pix",
				 "motivo":"Cancelado pelo cliente."}""".formatted(cliente)));
	}

	private static String mensagem(String tipo, String pedidoId, String dados) {
		return """
				{"id":"%s","tipo":"%s","pedidoId":"%s","ocorridoEm":"2026-11-12T12:00:00Z",
				 "correlationId":"corr-teste","dados":%s}""".formatted(UUID.randomUUID(), tipo, pedidoId, dados);
	}

	private ResultActions comoDono(String pedidoId) throws Exception {
		return mvc.perform(get("/pagamentos/" + pedidoId).header(UsuarioLogado.ID, cliente.toString())
				.header(UsuarioLogado.PERFIL, "cliente")).andExpect(status().isOk());
	}

	private static BancoIndisponivelException indisponivel(String motivo) {
		return new BancoIndisponivelException(motivo, false, null);
	}

	// Todos os eventos publicados no tópico pagamentos com a chave = id do pedido
	private List<String> publicados(String pedidoId) {
		ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
		verify(kafka, atLeastOnce()).send(eq("pagamentos"), eq(pedidoId), mensagem.capture());
		return mensagem.getAllValues();
	}

	private String publicado(String pedidoId) {
		return publicados(pedidoId).getLast();
	}

}
