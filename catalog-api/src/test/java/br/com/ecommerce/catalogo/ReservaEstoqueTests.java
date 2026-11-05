package br.com.ecommerce.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.JsonPath;

import br.com.ecommerce.catalogo.document.Estoque;
import br.com.ecommerce.catalogo.document.Produto;
import br.com.ecommerce.catalogo.evento.PedidosListener;
import br.com.ecommerce.catalogo.repository.ProdutoRepository;
import br.com.ecommerce.catalogo.repository.ReservaRepository;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;

/**
 * A reação ao pedido.criado: o teste faz o papel do Kafka, entregando a mensagem ao listener,
 * e olha o que seria publicado no tópico estoque.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservaEstoqueTests {

	static TransitionWalker.ReachedState<RunningMongodProcess> mongod;

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		if (mongod == null) {
			mongod = Mongod.instance().start(Version.Main.V8_0);
		}
		ServerAddress endereco = mongod.current().getServerAddress();
		registry.add("spring.mongodb.uri",
				() -> "mongodb://" + endereco.getHost() + ":" + endereco.getPort() + "/catalogo-reservas-test");
	}

	@AfterAll
	static void pararMongo() {
		if (mongod != null) {
			mongod.close();
		}
	}

	@MockitoBean
	KafkaTemplate<String, String> kafka;

	@Autowired
	PedidosListener listener;

	@Autowired
	ProdutoRepository produtos;

	@Autowired
	ReservaRepository reservas;

	@BeforeEach
	void kafkaNoAr() {
		when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
	}

	@Test
	void reservaTodosOsItensEPublicaEstoqueReservado() {
		String livro = produto("Effective Java", 10);
		String celular = produto("Smartphone X", 5);
		String pedidoId = UUID.randomUUID().toString();

		pedidoCriado(pedidoId, item(livro, 2), item(celular, 5));

		// Reservado não é vendido: disponivel diminui, reservado aumenta
		assertThat(estoque(livro)).isEqualTo(new Estoque(8, 2));
		assertThat(estoque(celular)).isEqualTo(new Estoque(0, 5));
		assertThat(reservas.findByPedidoId(pedidoId)).get()
			.satisfies(r -> assertThat(r.getStatus().name()).isEqualTo("RESERVADO"));

		String evento = publicado(pedidoId);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("estoque.reservado");
		assertThat(JsonPath.<String>read(evento, "$.pedidoId")).isEqualTo(pedidoId);
		// O correlationId do pedido.criado segue para o evento seguinte
		assertThat(JsonPath.<String>read(evento, "$.correlationId")).isEqualTo("corr-" + pedidoId.substring(0, 8));
		// Repassa o que veio no pedido.criado, sem perder centavos
		assertThat(evento).contains("\"valorTotal\":2239.70");
		assertThat(JsonPath.<String>read(evento, "$.dados.clienteEmail")).isEqualTo("cliente@loja.com");
		assertThat(JsonPath.<String>read(evento, "$.dados.formaPagamento")).isEqualTo("pix");
	}

	@Test
	void mesmoPedidoDuasVezesReservaUmaSoERepublicaOResultado() {
		String livro = produto("Effective Java", 10);
		String pedidoId = UUID.randomUUID().toString();

		pedidoCriado(pedidoId, item(livro, 3));
		clearInvocations(kafka);
		pedidoCriado(pedidoId, item(livro, 3)); // pelo menos uma vez: chegou de novo

		// O estoque saiu uma vez só, e o resultado foi publicado de novo (quem escuta aguenta repetido)
		assertThat(estoque(livro)).isEqualTo(new Estoque(7, 3));
		assertThat(JsonPath.<String>read(publicado(pedidoId), "$.tipo")).isEqualTo("estoque.reservado");
	}

	@Test
	void faltouUmItemNaoReservaNenhumEPublicaEstoqueInsuficiente() {
		String livro = produto("Effective Java", 10);
		String celular = produto("Smartphone X Pro", 5);
		String pedidoId = UUID.randomUUID().toString();

		pedidoCriado(pedidoId, item(livro, 2), item(celular, 6));

		// Tudo ou nada: o livro tinha sido reservado e foi devolvido
		assertThat(estoque(livro)).isEqualTo(new Estoque(10, 0));
		assertThat(estoque(celular)).isEqualTo(new Estoque(5, 0));

		String evento = publicado(pedidoId);
		assertThat(JsonPath.<String>read(evento, "$.tipo")).isEqualTo("estoque.insuficiente");
		assertThat(JsonPath.<String>read(evento, "$.dados.motivo"))
			.isEqualTo("Estoque insuficiente para Smartphone X Pro: pedido 6, disponível 5.");
		assertThat(JsonPath.<String>read(evento, "$.dados.clienteEmail")).isEqualTo("cliente@loja.com");
		assertThat(JsonPath.<String>read(evento, "$.dados.usuarioId")).isEqualTo("ana");
	}

	@Test
	void produtoDesativadoNaoEhReservado() {
		String livro = produto("Livro fora de linha", 10);
		Produto produto = produtos.findById(livro).orElseThrow();
		produto.setAtivo(false);
		produtos.save(produto);
		String pedidoId = UUID.randomUUID().toString();

		pedidoCriado(pedidoId, item(livro, 1));

		assertThat(estoque(livro)).isEqualTo(new Estoque(10, 0));
		assertThat(JsonPath.<String>read(publicado(pedidoId), "$.dados.motivo"))
			.isEqualTo("Produto indisponível: Livro fora de linha.");
	}

	@Test
	void pedidosAoMesmoTempoNaoDeixamOEstoqueNegativo() throws Exception {
		String celular = produto("Smartphone Max", 3);

		// 8 pedidos de 1 unidade disputando 3 unidades
		ExecutorService threads = Executors.newFixedThreadPool(8);
		try {
			List<Future<?>> envios = new java.util.ArrayList<>();
			for (int i = 0; i < 8; i++) {
				envios.add(threads.submit(() -> pedidoCriado(UUID.randomUUID().toString(), item(celular, 1))));
			}
			for (Future<?> envio : envios) {
				envio.get();
			}
		} finally {
			threads.shutdown();
		}

		assertThat(estoque(celular)).isEqualTo(new Estoque(0, 3));
	}

	@Test
	void outrosTiposDoTopicoPedidosSaoIgnorados() {
		String livro = produto("Effective Java", 10);
		String pedidoId = UUID.randomUUID().toString();

		listener.receber(mensagem("pedido.cancelado", pedidoId, "{}"));

		assertThat(estoque(livro)).isEqualTo(new Estoque(10, 0));
		verify(kafka, never()).send(anyString(), anyString(), anyString());
	}

	// ---------- apoio ----------

	private String produto(String nome, int disponivel) {
		Produto produto = new Produto("RES-" + UUID.randomUUID().toString().substring(0, 8), nome, "livros",
				new BigDecimal("10.00"), Map.of(), new Estoque(disponivel, 0));
		return produtos.save(produto).getId();
	}

	private Estoque estoque(String produtoId) {
		return produtos.findById(produtoId).orElseThrow().getEstoque();
	}

	private static String item(String produtoId, int quantidade) {
		return "{\"produtoId\":\"%s\",\"nome\":\"x\",\"precoUnitario\":10.00,\"quantidade\":%d}"
			.formatted(produtoId, quantidade);
	}

	// Como o pedidos publica: valorTotal com centavos, cartão só com os 4 últimos dígitos
	private void pedidoCriado(String pedidoId, String... itens) {
		listener.receber(mensagem("pedido.criado", pedidoId, """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valorTotal":2239.70,
				 "formaPagamento":"pix","itens":[%s]}""".formatted(String.join(",", itens))));
	}

	private static String mensagem(String tipo, String pedidoId, String dados) {
		return """
				{"id":"%s","tipo":"%s","pedidoId":"%s","ocorridoEm":"2026-11-05T12:00:00Z",
				 "correlationId":"corr-%s","dados":%s}"""
			.formatted(UUID.randomUUID(), tipo, pedidoId, pedidoId.substring(0, 8), dados);
	}

	// O último evento publicado no tópico estoque, com a chave = id do pedido
	private String publicado(String pedidoId) {
		ArgumentCaptor<String> mensagem = ArgumentCaptor.forClass(String.class);
		verify(kafka, atLeastOnce()).send(eq("estoque"), eq(pedidoId), mensagem.capture());
		return mensagem.getValue();
	}

}
