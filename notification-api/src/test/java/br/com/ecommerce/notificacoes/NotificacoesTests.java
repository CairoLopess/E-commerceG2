package br.com.ecommerce.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.ServerSocket;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.ecommerce.notificacoes.evento.KafkaSaude;
import br.com.ecommerce.notificacoes.evento.NotificacoesListener;

/**
 * O teste faz o papel do Kafka (entrega a mensagem ao listener) e o SMTP é simulado: olha o
 * e-mail que sairia para o Mailpit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NotificacoesTests {

	@MockitoBean
	JavaMailSender smtp;

	@MockitoBean
	KafkaSaude kafka;

	@Autowired
	MockMvc mvc;

	// Um "SMTP" que só aceita conexão: o /health só testa se dá para conectar
	static ServerSocket smtpFalso;

	@DynamicPropertySource
	static void smtp(DynamicPropertyRegistry registry) throws IOException {
		if (smtpFalso == null) {
			smtpFalso = new ServerSocket(0);
		}
		registry.add("spring.mail.host", () -> "127.0.0.1");
		registry.add("spring.mail.port", () -> smtpFalso.getLocalPort());
	}

	@AfterAll
	static void fecharSmtp() throws IOException {
		smtpFalso.close();
	}

	@Autowired
	NotificacoesListener listener;

	@Test
	void pedidoCriadoMandaRecebemosOSeuPedido(CapturedOutput saida) {
		listener.receber(mensagem("pedido.criado", """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valorTotal":2239.70,"formaPagamento":"pix",
				 "itens":[{"produtoId":"1","nome":"Effective Java","precoUnitario":119.90,"quantidade":2},
				          {"produtoId":"2","nome":"Smartphone X","precoUnitario":1999.90,"quantidade":1}]}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getTo()).containsExactly("cliente@loja.com");
		assertThat(email.getFrom()).isEqualTo("loja@ecommerce.local");
		assertThat(email.getSubject()).isEqualTo("Recebemos o seu pedido");
		assertThat(email.getText())
			.contains("pedido 7d0c1b2e-0000-0000-0000-000000000001")
			.contains("- 2x Effective Java (R$ 119,90)")
			.contains("- 1x Smartphone X (R$ 1.999,90)")
			.contains("Total: R$ 2.239,70")
			.contains("Pagamento: pix");
		// O correlationId do evento vai para o log
		assertThat(saida.getOut()).contains("[corr-teste]").contains("enviado para c***@loja.com")
			.doesNotContain("cliente@loja.com");
	}

	@Test
	void estoqueInsuficienteMandaOMotivo() {
		listener.receber(mensagem("estoque.insuficiente", """
				{"motivo":"Estoque insuficiente para Smartphone X Pro: pedido 6, disponível 5.",
				 "usuarioId":"ana","clienteEmail":"cliente@loja.com"}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getTo()).containsExactly("cliente@loja.com");
		assertThat(email.getSubject()).isEqualTo("Seu pedido foi cancelado: sem estoque");
		assertThat(email.getText()).contains("Estoque insuficiente para Smartphone X Pro: pedido 6, disponível 5.");
	}

	@Test
	void pagamentoAprovadoConfirmaOPedido() {
		listener.receber(mensagem("pagamento.aprovado", """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valor":2239.70,"codigoTransacao":"TX-F6A1",
				 "tentativas":2}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getSubject()).isEqualTo("Pagamento aprovado: pedido confirmado");
		assertThat(email.getText()).contains("Valor: R$ 2.239,70").contains("Transação: TX-F6A1");
	}

	@Test
	void pagamentoRecusadoMandaOMotivo() {
		listener.receber(mensagem("pagamento.recusado", """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valor":60.00,
				 "motivo":"banco indisponível (HTTP 503)","tentativas":3}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getSubject()).isEqualTo("Seu pedido foi cancelado: pagamento recusado");
		assertThat(email.getText()).contains("Motivo: banco indisponível (HTTP 503)").contains("Nada foi cobrado");
	}

	@Test
	void pagamentoEstornadoAvisaODinheiroDeVolta() {
		listener.receber(mensagem("pagamento.estornado", """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valor":60.00,"codigoTransacao":"TX-F6A1"}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getSubject()).isEqualTo("Seu pagamento foi estornado");
		assertThat(email.getText()).contains("Valor devolvido: R$ 60,00").contains("Transação: TX-F6A1");
	}

	@Test
	void eventosQueNaoInteressamNaoMandamEmail() {
		// estoque.reservado: o cliente fica sabendo quando o pagamento for aprovado
		listener.receber(mensagem("estoque.reservado", "{\"clienteEmail\":\"cliente@loja.com\"}"));
		// pedido.cancelado: foi o próprio cliente; se havia cobrança, vem o e-mail do estorno
		listener.receber(mensagem("pedido.cancelado", "{\"clienteEmail\":\"cliente@loja.com\"}"));
		// Sem e-mail no evento: não tem para quem mandar
		listener.receber(mensagem("estoque.insuficiente", "{\"motivo\":\"x\"}"));

		verify(smtp, never()).send(any(SimpleMailMessage.class));
	}

	// ---------- /health ----------

	@Test
	void healthComKafkaESmtpNoArDevolveOk() throws Exception {
		when(kafka.noAr()).thenReturn(true);

		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servico").value("notificacoes"))
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.dependencias.kafka").value("ok"))
			.andExpect(jsonPath("$.dependencias.smtp").value("ok"));
	}

	@Test
	void healthSemKafkaEstaForaDoAr() throws Exception {
		when(kafka.noAr()).thenReturn(false);

		mvc.perform(get("/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("fora do ar"))
			.andExpect(jsonPath("$.dependencias.kafka").value("fora"));
	}

	private SimpleMailMessage enviado() {
		ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(smtp).send(email.capture());
		return email.getValue();
	}

	private static String mensagem(String tipo, String dados) {
		return """
				{"id":"e1","tipo":"%s","pedidoId":"7d0c1b2e-0000-0000-0000-000000000001",
				 "ocorridoEm":"2026-11-05T12:00:00Z","correlationId":"corr-teste","dados":%s}""".formatted(tipo, dados);
	}

}
