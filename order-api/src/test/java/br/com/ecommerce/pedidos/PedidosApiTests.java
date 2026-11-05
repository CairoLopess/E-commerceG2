package br.com.ecommerce.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

import br.com.ecommerce.pedidos.catalogo.CatalogoClient;
import br.com.ecommerce.pedidos.catalogo.CatalogoIndisponivelException;
import br.com.ecommerce.pedidos.catalogo.ProdutoCatalogo;
import br.com.ecommerce.pedidos.evento.EstoqueListener;
import br.com.ecommerce.pedidos.security.UsuarioLogado;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PedidosApiTests {

	static final String LIVRO = "665f1a000000000000000001";
	static final String CELULAR = "665f1a000000000000000002";

	static final String CHECKOUT_CARTAO = """
			{"enderecoEntrega": {"cep":"90010-000","logradouro":"Rua dos Andradas","numero":"100",
			  "cidade":"Porto Alegre","uf":"rs"},
			 "formaPagamento":"cartao","numeroCartao":"4111111111111111"}""";

	@Autowired
	MockMvc mvc;

	// O catálogo é outro serviço: aqui ele é simulado (no ar, sem um produto, fora do ar)
	@MockitoBean
	CatalogoClient catalogo;

	// O Kafka também: o teste vê o que seria publicado. Os listeners não ligam (application-test);
	// o teste chama o listener direto, como o Kafka faria.
	@MockitoBean
	KafkaTemplate<String, String> kafka;

	@Autowired
	EstoqueListener estoqueListener;

	// Cada teste é um cliente novo, com o carrinho vazio. Nos testes não há gateway:
	// o teste faz o papel dele e manda X-User-Id e X-User-Email.
	final UUID usuario = UUID.randomUUID();

	@BeforeEach
	void catalogoEKafkaNoAr() {
		catalogoTem(livro(new BigDecimal("119.90")), celular());
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
	}

	// ---------- identidade ----------

	@Test
	void semXUserIdDevolve401() throws Exception {
		mvc.perform(get("/carrinho"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.erro").value("Usuário não identificado. Faça login e acesse pelo gateway."));

		// O checkout precisa também do e-mail (vai para o pedido)
		mvc.perform(post("/pedidos").header(UsuarioLogado.ID, usuario.toString())
				.contentType(MediaType.APPLICATION_JSON).content(CHECKOUT_CARTAO))
			.andExpect(status().isUnauthorized());
	}

	// ---------- carrinho ----------

	@Test
	void carrinhoComecaVazio() throws Exception {
		mvc.perform(logado(get("/carrinho")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itens", hasSize(0)))
			.andExpect(jsonPath("$.total").value(0));
	}

	@Test
	void adicionarSomaAQuantidadeEOCarrinhoMostraPrecosAtuais() throws Exception {
		adicionar(LIVRO, 1)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.nome").value("Effective Java"))
			.andExpect(jsonPath("$.precoUnitario").value(119.90))
			.andExpect(jsonPath("$.quantidade").value(1));

		// O mesmo produto de novo: soma, não duplica a linha
		adicionar(LIVRO, 2).andExpect(status().isCreated()).andExpect(jsonPath("$.quantidade").value(3));
		adicionar(CELULAR, 1).andExpect(status().isCreated());

		mvc.perform(logado(get("/carrinho")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itens", hasSize(2)))
			.andExpect(jsonPath("$.itens[?(@.produtoId == '" + LIVRO + "')].subtotal").value(359.70))
			.andExpect(jsonPath("$.total").value(2359.60))
			.andExpect(jsonPath("$.aviso").doesNotExist());
	}

	@Test
	void adicionarValidaOsDados() throws Exception {
		mvc.perform(logado(post("/carrinho/itens")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"produtoId\":\"" + LIVRO + "\",\"quantidade\":0}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Dados inválidos."))
			.andExpect(jsonPath("$.detalhes[0]").value("quantidade deve ser maior que zero"));

		mvc.perform(logado(post("/carrinho/itens")).contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detalhes", hasSize(2)));
	}

	@Test
	void adicionarProdutoQueNaoExisteDevolve404() throws Exception {
		adicionar("id-que-nao-existe", 1)
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Produto não encontrado."));
	}

	@Test
	void removerItemDevolve204E404QuandoNaoEstaNoCarrinho() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());

		mvc.perform(logado(delete("/carrinho/itens/" + LIVRO))).andExpect(status().isNoContent());
		mvc.perform(logado(delete("/carrinho/itens/" + LIVRO)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Produto não está no carrinho."));
	}

	// ---------- checkout ----------

	@Test
	void checkoutCriaPedidoAguardandoEstoqueECopiaOsPrecos() throws Exception {
		adicionar(LIVRO, 2).andExpect(status().isCreated());
		adicionar(CELULAR, 1).andExpect(status().isCreated());

		String resposta = checkout(CHECKOUT_CARTAO)
			.andExpect(status().isAccepted())
			.andExpect(header().string("Location", startsWith("/pedidos/")))
			.andExpect(jsonPath("$.status").value("AGUARDANDO_ESTOQUE"))
			.andExpect(jsonPath("$.valorTotal").value(2239.70))
			.andExpect(jsonPath("$.clienteEmail").value("cliente@loja.com"))
			.andExpect(jsonPath("$.enderecoEntrega.uf").value("RS"))
			.andExpect(jsonPath("$.formaPagamento").value("cartao"))
			.andExpect(jsonPath("$.cartaoFinal").value("1111"))
			.andExpect(jsonPath("$.numeroCartao").doesNotExist())
			.andExpect(jsonPath("$.itens", hasSize(2)))
			.andReturn().getResponse().getContentAsString();
		String pedidoId = JsonPath.read(resposta, "$.id");

		// O carrinho foi esvaziado
		mvc.perform(logado(get("/carrinho"))).andExpect(jsonPath("$.itens", hasSize(0)));

		// O produto mudou de preço no catálogo: o pedido de ontem não muda
		catalogoTem(livro(new BigDecimal("150.00")), celular());
		mvc.perform(logado(get("/pedidos/" + pedidoId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itens[?(@.produtoId == '" + LIVRO + "')].precoUnitario").value(119.90))
			.andExpect(jsonPath("$.itens[?(@.produtoId == '" + LIVRO + "')].nome").value("Effective Java"))
			.andExpect(jsonPath("$.valorTotal").value(2239.70));

		mvc.perform(logado(get("/pedidos")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)))
			.andExpect(jsonPath("$[0].id").value(pedidoId));
	}

	@Test
	void checkoutComPixNaoTemCartao() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());

		checkout(CHECKOUT_CARTAO.replace("\"formaPagamento\":\"cartao\",\"numeroCartao\":\"4111111111111111\"",
				"\"formaPagamento\":\"pix\""))
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.formaPagamento").value("pix"))
			.andExpect(jsonPath("$.cartaoFinal").doesNotExist());
	}

	@Test
	void checkoutComCarrinhoVazioDevolve400() throws Exception {
		checkout(CHECKOUT_CARTAO)
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Carrinho vazio."));
	}

	@Test
	void checkoutValidaEnderecoEPagamento() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());

		checkout("{\"formaPagamento\":\"cheque\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detalhes", hasSize(2)));

		checkout(CHECKOUT_CARTAO.replace(",\"numeroCartao\":\"4111111111111111\"", ""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("numeroCartao é obrigatório para pagamento com cartão."));
	}

	@Test
	void checkoutComProdutoQueSaiuDoCatalogoDevolve409EMantemOCarrinho() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());
		catalogoTem(celular()); // o livro foi desativado

		checkout(CHECKOUT_CARTAO)
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.erro").value("Produto indisponível: " + LIVRO + "."));

		mvc.perform(logado(get("/carrinho")))
			.andExpect(jsonPath("$.itens", hasSize(1)))
			.andExpect(jsonPath("$.itens[0].disponivel").value(false));
	}

	@Test
	void checkoutPublicaPedidoCriadoComOEnvelopeEAChaveDoPedido() throws Exception {
		adicionar(LIVRO, 2).andExpect(status().isCreated());
		adicionar(CELULAR, 1).andExpect(status().isCreated());

		String resposta = mvc.perform(logado(post("/pedidos")).header(UsuarioLogado.REQUEST_ID, "3c37be51-checkout")
				.contentType(MediaType.APPLICATION_JSON).content(CHECKOUT_CARTAO))
			.andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		String pedidoId = JsonPath.read(resposta, "$.id");

		ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
		verify(kafka).send(eq("pedidos"), eq(pedidoId), mensagem.capture());
		String evento = mensagem.getValue();

		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("pedido.criado");
		assertThat(JsonPath.<String>read(evento, "$.pedidoId")).isEqualTo(pedidoId);
		assertThat(JsonPath.<String>read(evento, "$.id")).isNotBlank();
		assertThat(JsonPath.<String>read(evento, "$.ocorridoEm")).endsWith("Z");
		// O X-Request-Id do Kong vira o correlationId do evento
		assertThat(JsonPath.<String>read(evento, "$.correlationId")).isEqualTo("3c37be51-checkout");
		assertThat(JsonPath.<String>read(evento, "$.dados.usuarioId")).isEqualTo(usuario.toString());
		assertThat(JsonPath.<String>read(evento, "$.dados.clienteEmail")).isEqualTo("cliente@loja.com");
		assertThat(JsonPath.<Double>read(evento, "$.dados.valorTotal")).isEqualTo(2239.70);
		assertThat(JsonPath.<String>read(evento, "$.dados.formaPagamento")).isEqualTo("cartao");
		assertThat(JsonPath.<String>read(evento, "$.dados.cartaoFinal")).isEqualTo("1111");
		assertThat(JsonPath.<Integer>read(evento, "$.dados.itens.length()")).isEqualTo(2);
		assertThat(evento).contains("\"produtoId\":\"" + CELULAR + "\"");
		// O número do cartão nunca vai para o evento: ele fica guardado no tópico e muita gente lê
		assertThat(evento).doesNotContain("4111111111111111");
	}

	@Test
	void checkoutNaoConfereEstoqueQuemDecideEhOCatalogo() throws Exception {
		adicionar(CELULAR, 5).andExpect(status().isCreated()); // o catálogo tem 2

		checkout(CHECKOUT_CARTAO)
			.andExpect(status().isAccepted())
			.andExpect(jsonPath("$.status").value("AGUARDANDO_ESTOQUE"));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void kafkaForaOPedidoFicaGravadoEOErroVaiParaOLog(CapturedOutput saida) throws Exception {
		when(kafka.send(anyString(), anyString(), anyString()))
			.thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker fora")));
		String pedidoId = criarPedido();

		statusDoPedido(pedidoId).andExpect(jsonPath("$.status").value("AGUARDANDO_ESTOQUE"));
		assertThat(saida.getOut()).contains("Pedido " + pedidoId + " gravado, mas o pedido.criado não foi publicado");
	}

	@Test
	void checkoutQueFalhaNaoPublicaNada() throws Exception {
		checkout(CHECKOUT_CARTAO).andExpect(status().isBadRequest()); // carrinho vazio

		verify(kafka, never()).send(anyString(), anyString(), anyString());
	}

	// ---------- eventos do estoque ----------

	@Test
	void estoqueReservadoLevaParaAguardandoPagamentoERepetidoNaoFazNada() throws Exception {
		String pedidoId = criarPedido();

		eventoDoEstoque("estoque.reservado", pedidoId, "{\"valorTotal\":119.90}");
		statusDoPedido(pedidoId).andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"));

		// Pelo menos uma vez: o mesmo evento chega de novo
		eventoDoEstoque("estoque.reservado", pedidoId, "{\"valorTotal\":119.90}");
		statusDoPedido(pedidoId)
			.andExpect(jsonPath("$.status").value("AGUARDANDO_PAGAMENTO"))
			.andExpect(jsonPath("$.motivoCancelamento").doesNotExist());
	}

	@Test
	void estoqueInsuficienteCancelaComOMotivo() throws Exception {
		String pedidoId = criarPedido();

		eventoDoEstoque("estoque.insuficiente", pedidoId,
				"{\"motivo\":\"Estoque insuficiente para Effective Java.\",\"clienteEmail\":\"cliente@loja.com\"}");

		statusDoPedido(pedidoId)
			.andExpect(jsonPath("$.status").value("CANCELADO"))
			.andExpect(jsonPath("$.motivoCancelamento").value("Estoque insuficiente para Effective Java."));
	}

	@Test
	void eventoAtrasadoNaoLevaOPedidoParaTras() throws Exception {
		String pedidoId = criarPedido();
		mvc.perform(logado(post("/pedidos/" + pedidoId + "/cancelar"))).andExpect(status().isOk());

		// O cliente cancelou antes de o catálogo responder: o reservado não "descancela"
		eventoDoEstoque("estoque.reservado", pedidoId, "{}");

		statusDoPedido(pedidoId)
			.andExpect(jsonPath("$.status").value("CANCELADO"))
			.andExpect(jsonPath("$.motivoCancelamento").value("Cancelado pelo cliente."));
	}

	@Test
	void eventoDeOutroTipoOuDePedidoQueNaoExisteEhIgnorado() throws Exception {
		String pedidoId = criarPedido();

		eventoDoEstoque("estoque.qualquer-coisa", pedidoId, "{}");
		eventoDoEstoque("estoque.reservado", UUID.randomUUID().toString(), "{}");
		eventoDoEstoque("estoque.reservado", "nao-eh-uuid", "{}");

		statusDoPedido(pedidoId).andExpect(jsonPath("$.status").value("AGUARDANDO_ESTOQUE"));
	}

	@Test
	void catalogoForaDevolve503NoCheckoutMasOCarrinhoEOsPedidosContinuam() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());
		doThrow(new CatalogoIndisponivelException()).when(catalogo).buscarProdutos(anyCollection(), any());

		checkout(CHECKOUT_CARTAO)
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.erro").value("Catálogo indisponível no momento. Tente de novo em instantes."));

		mvc.perform(logado(get("/carrinho")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itens[0].produtoId").value(LIVRO))
			.andExpect(jsonPath("$.itens[0].quantidade").value(1))
			.andExpect(jsonPath("$.itens[0].precoUnitario").doesNotExist())
			.andExpect(jsonPath("$.aviso").isString());

		mvc.perform(logado(get("/pedidos"))).andExpect(status().isOk());
	}

	@Test
	void repassaOXRequestIdAoCatalogo() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());

		mvc.perform(logado(get("/carrinho")).header(UsuarioLogado.REQUEST_ID, "3c37be51-teste"))
			.andExpect(status().isOk());

		verify(catalogo).buscarProdutos(anyCollection(), eq("3c37be51-teste"));
	}

	// ---------- pedidos ----------

	@Test
	void pedidoDeOutroUsuarioDevolve404() throws Exception {
		String pedidoId = criarPedido();
		String outro = UUID.randomUUID().toString();

		mvc.perform(get("/pedidos/" + pedidoId).header(UsuarioLogado.ID, outro))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Pedido não encontrado."));
		mvc.perform(post("/pedidos/" + pedidoId + "/cancelar").header(UsuarioLogado.ID, outro))
			.andExpect(status().isNotFound());
		mvc.perform(get("/pedidos").header(UsuarioLogado.ID, outro))
			.andExpect(jsonPath("$", hasSize(0)));
	}

	@Test
	void cancelarDevolve200EDepois409() throws Exception {
		String pedidoId = criarPedido();

		mvc.perform(logado(post("/pedidos/" + pedidoId + "/cancelar")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("CANCELADO"))
			.andExpect(jsonPath("$.motivoCancelamento").value("Cancelado pelo cliente."));

		mvc.perform(logado(post("/pedidos/" + pedidoId + "/cancelar")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.erro").value("Pedido com status CANCELADO não pode ser cancelado."));
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void logDeCadaRequisicaoSaiComOXRequestId(CapturedOutput saida) throws Exception {
		mvc.perform(get("/health").header("X-Request-Id", "3c37be51-teste")).andExpect(status().isOk());
		// Id forjado (com quebra de linha, para inventar uma linha no log) não entra
		mvc.perform(get("/health").header("X-Request-Id", "falso\nlinha forjada")).andExpect(status().isOk());

		assertThat(saida.getOut().lines().filter(linha -> linha.contains("GET /health -> 200")))
			.anyMatch(linha -> linha.contains("[3c37be51-teste]"));
		assertThat(saida.getOut()).doesNotContain("linha forjada");
	}

	@Test
	void healthDevolveOk() throws Exception {
		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.banco").value("ok"));
	}

	// ---------- apoio ----------

	private MockHttpServletRequestBuilder logado(MockHttpServletRequestBuilder requisicao) {
		return requisicao.header(UsuarioLogado.ID, usuario.toString()).header(UsuarioLogado.EMAIL, "cliente@loja.com");
	}

	private org.springframework.test.web.servlet.ResultActions adicionar(String produtoId, int quantidade)
			throws Exception {
		return mvc.perform(logado(post("/carrinho/itens")).contentType(MediaType.APPLICATION_JSON)
				.content("{\"produtoId\":\"" + produtoId + "\",\"quantidade\":" + quantidade + "}"));
	}

	private org.springframework.test.web.servlet.ResultActions checkout(String corpo) throws Exception {
		return mvc.perform(logado(post("/pedidos")).contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private String criarPedido() throws Exception {
		adicionar(LIVRO, 1).andExpect(status().isCreated());
		String resposta = checkout(CHECKOUT_CARTAO).andExpect(status().isAccepted())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions statusDoPedido(String pedidoId) throws Exception {
		return mvc.perform(logado(get("/pedidos/" + pedidoId))).andExpect(status().isOk());
	}

	// O que o Kafka entregaria ao listener: a mensagem como texto, no envelope
	private void eventoDoEstoque(String tipo, String pedidoId, String dados) {
		estoqueListener.receber("""
				{"id":"%s","tipo":"%s","pedidoId":"%s","ocorridoEm":"2026-11-05T12:00:00Z",
				 "correlationId":"teste-kafka","dados":%s}""".formatted(UUID.randomUUID(), tipo, pedidoId, dados));
	}

	private void catalogoTem(ProdutoCatalogo... produtos) {
		doAnswer(chamada -> {
			Collection<String> ids = chamada.getArgument(0);
			return Stream.of(produtos)
					.filter(produto -> ids.contains(produto.id()))
					.collect(Collectors.toMap(ProdutoCatalogo::id, Function.identity()));
		}).when(catalogo).buscarProdutos(anyCollection(), any());
	}

	private static ProdutoCatalogo livro(BigDecimal preco) {
		return new ProdutoCatalogo(LIVRO, "Effective Java", preco, new ProdutoCatalogo.Estoque(10));
	}

	private static ProdutoCatalogo celular() {
		return new ProdutoCatalogo(CELULAR, "Smartphone X", new BigDecimal("1999.90"), new ProdutoCatalogo.Estoque(2));
	}

}
