package br.com.ecommerce.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.com.ecommerce.catalogo.cache.VitrineCache;
import br.com.ecommerce.catalogo.document.Estoque;
import br.com.ecommerce.catalogo.evento.KafkaSaude;
import br.com.ecommerce.catalogo.document.Produto;
import br.com.ecommerce.catalogo.repository.CategoriaRepository;
import br.com.ecommerce.catalogo.repository.ProdutoRepository;
import br.com.ecommerce.catalogo.seed.DataSeeder;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogoApiTests {

	// Mongo de verdade, baixado e iniciado pelo flapdoodle (na primeira vez demora o download)
	static TransitionWalker.ReachedState<RunningMongodProcess> mongod;

	@DynamicPropertySource
	static void mongoProperties(DynamicPropertyRegistry registry) {
		if (mongod == null) {
			mongod = Mongod.instance().start(Version.Main.V8_0);
		}
		ServerAddress endereco = mongod.current().getServerAddress();
		registry.add("spring.mongodb.uri",
				() -> "mongodb://" + endereco.getHost() + ":" + endereco.getPort() + "/catalogo-test");
	}

	@AfterAll
	static void pararMongo() {
		if (mongod != null) {
			mongod.close();
		}
	}

	@Autowired
	MockMvc mvc;

	@Autowired
	DataSeeder seeder;

	@Autowired
	ProdutoRepository produtoRepository;

	@Autowired
	CategoriaRepository categoriaRepository;

	@Autowired
	MemoriaCacheStore cacheStore;

	@Autowired
	VitrineCache vitrineCache;

	// O /health pergunta ao Kafka; nos testes não há Kafka
	@MockitoBean
	KafkaSaude kafkaSaude;

	@BeforeEach
	void kafkaNoArECacheDesligado() {
		when(kafkaSaude.noAr()).thenReturn(true);
		cacheStore.desligar();
	}

	@Test
	void seedCriaCategoriasEProdutosEEhIdempotente() throws Exception {
		assertThat(categoriaRepository.count()).isGreaterThanOrEqualTo(3);
		assertThat(produtoRepository.count()).isGreaterThanOrEqualTo(10);
		long categorias = categoriaRepository.count();
		long produtos = produtoRepository.count();

		seeder.run(null);

		assertThat(categoriaRepository.count()).isEqualTo(categorias);
		assertThat(produtoRepository.count()).isEqualTo(produtos);
	}

	@Test
	void indiceUnicoDeSkuFuncionaNoBanco() {
		// Fura o service de propósito: o índice único do Mongo é a última linha de defesa
		Produto duplicado = new Produto("CEL-001", "Duplicado", "celulares", BigDecimal.TEN, Map.of(),
				new Estoque(1, 0));
		assertThatThrownBy(() -> produtoRepository.save(duplicado)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void listaFiltraPorCategoriaEBusca() throws Exception {
		mvc.perform(get("/produtos").param("categoria", "livros"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
			.andExpect(jsonPath("$[*].categoria", everyItem(is("livros"))))
			.andExpect(jsonPath("$[*].atributos.autor", hasItem("Joshua Bloch")));

		mvc.perform(get("/produtos").param("busca", "SMARTPHONE x"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].sku", hasItem("CEL-001")))
			.andExpect(jsonPath("$[*].sku", hasItem("CEL-002")))
			.andExpect(jsonPath("$[*].categoria", everyItem(is("celulares"))));
	}

	@Test
	void listaPorIdsDevolveSoOsAtivosQueExistem() throws Exception {
		// É a chamada do checkout do pedidos: todos os produtos do carrinho de uma vez
		String a = criarProduto(skuNovo());
		String b = criarProduto(skuNovo());
		String desativado = criarProduto(skuNovo());
		mvc.perform(delete("/produtos/" + desativado)).andExpect(status().isNoContent());

		mvc.perform(get("/produtos").param("ids", a + "," + b + "," + desativado + ",id-que-nao-existe"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(2)))
			.andExpect(jsonPath("$[*].id", hasItem(a)))
			.andExpect(jsonPath("$[*].id", hasItem(b)))
			.andExpect(jsonPath("$[*].id", not(hasItem(desativado))));
	}

	@Test
	void buscaUmProdutoE404QuandoNaoExiste() throws Exception {
		String id = criarProduto(skuNovo());

		mvc.perform(get("/produtos/" + id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.preco").value(199.90))
			.andExpect(jsonPath("$.estoque.disponivel").value(7))
			.andExpect(jsonPath("$.estoque.reservado").value(0));

		mvc.perform(get("/produtos/id-que-nao-existe"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Produto não encontrado."));
	}

	@Test
	void criarComSkuRepetidoDevolve409() throws Exception {
		mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content(produtoJson("CEL-001")))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.erro").value("Já existe um produto com este SKU."));
	}

	@Test
	void criarInvalidoDevolve400ComDetalhes() throws Exception {
		mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Sem sku\",\"categoria\":\"livros\",\"preco\":-5}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Dados inválidos."))
			.andExpect(jsonPath("$.detalhes", hasItem("preco deve ser maior que zero")))
			.andExpect(jsonPath("$.detalhes", hasItem("sku é obrigatório")));
	}

	@Test
	void criarComCategoriaInexistenteDevolve400() throws Exception {
		mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
				.content("{\"sku\":\"" + skuNovo() + "\",\"nome\":\"X\",\"categoria\":\"brinquedos\",\"preco\":10}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Categoria 'brinquedos' não existe."));
	}

	@Test
	void putSubstituiProdutoEPreservaReservado() throws Exception {
		String sku = skuNovo();
		String id = criarProduto(sku);

		mvc.perform(put("/produtos/" + id).contentType(MediaType.APPLICATION_JSON).content("""
				{"sku":"%s","nome":"Livro atualizado","categoria":"livros","preco":250.00,
				 "atributos":{"autor":"Outra Pessoa","paginas":300}}""".formatted(sku)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome").value("Livro atualizado"))
			.andExpect(jsonPath("$.atributos.autor").value("Outra Pessoa"))
			.andExpect(jsonPath("$.estoque.disponivel").value(7));

		mvc.perform(put("/produtos/" + id).contentType(MediaType.APPLICATION_JSON).content(produtoJson("CEL-001")))
			.andExpect(status().isConflict());

		mvc.perform(put("/produtos/id-que-nao-existe").contentType(MediaType.APPLICATION_JSON)
				.content(produtoJson(skuNovo())))
			.andExpect(status().isNotFound());
	}

	@Test
	void patchEstoqueAjustaEBloqueiaNegativo() throws Exception {
		String id = criarProduto(skuNovo());

		mvc.perform(patch("/produtos/" + id + "/estoque").contentType(MediaType.APPLICATION_JSON)
				.content("{\"disponivel\":25}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.estoque.disponivel").value(25));

		mvc.perform(patch("/produtos/" + id + "/estoque").contentType(MediaType.APPLICATION_JSON)
				.content("{\"disponivel\":-1}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detalhes", hasItem("estoque não pode ser negativo")));

		mvc.perform(patch("/produtos/id-que-nao-existe/estoque").contentType(MediaType.APPLICATION_JSON)
				.content("{\"disponivel\":1}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void deleteDesativaSemApagar() throws Exception {
		String sku = skuNovo();
		String id = criarProduto(sku);

		mvc.perform(delete("/produtos/" + id)).andExpect(status().isNoContent());

		// Sai da vitrine...
		mvc.perform(get("/produtos/" + id)).andExpect(status().isNotFound());
		mvc.perform(get("/produtos").param("busca", "Livro de teste"))
			.andExpect(jsonPath("$[*].sku", not(hasItem(sku))));

		// ...mas continua no banco
		assertThat(produtoRepository.findById(id)).get().extracting(Produto::isAtivo).isEqualTo(false);

		mvc.perform(delete("/produtos/id-que-nao-existe")).andExpect(status().isNotFound());
	}

	@Test
	void categoriasListaECriaComSlugUnico() throws Exception {
		mvc.perform(get("/categorias"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].slug", hasItem("celulares")));

		String slug = "cat-" + UUID.randomUUID().toString().substring(0, 8);
		String corpo = "{\"nome\":\"Nova\",\"slug\":\"" + slug + "\"}";
		mvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.slug").value(slug));

		mvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.erro").value("Já existe uma categoria com este slug."));

		mvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Ruim\",\"slug\":\"Com Espaço\"}"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@ExtendWith(OutputCaptureExtension.class)
	void logDeCadaRequisicaoSaiComOXRequestId(CapturedOutput saida) throws Exception {
		mvc.perform(get("/categorias").header("X-Request-Id", "3c37be51-teste")).andExpect(status().isOk());
		// Id forjado (com quebra de linha, para inventar uma linha no log) não entra
		mvc.perform(get("/categorias").header("X-Request-Id", "falso\nlinha forjada")).andExpect(status().isOk());
		// O /health não vai para o log: o Docker o chama a cada 10 s
		mvc.perform(get("/health").header("X-Request-Id", "health-teste"));

		assertThat(saida.getOut().lines().filter(linha -> linha.contains("GET /categorias -> 200")))
			.anyMatch(linha -> linha.contains("[3c37be51-teste]"));
		assertThat(saida.getOut()).doesNotContain("linha forjada").doesNotContain("GET /health");
	}

	// ---------- /health ----------

	@Test
	void healthComTudoNoArDevolveOk() throws Exception {
		ligarCache();

		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servico").value("catalogo"))
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.dependencias.banco").value("ok"))
			.andExpect(jsonPath("$.dependencias.kafka").value("ok"))
			.andExpect(jsonPath("$.dependencias.cache").value("ok"));
	}

	@Test
	void semCacheFicaDegradadoMasNoAr() throws Exception {
		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("degradado"))
			.andExpect(jsonPath("$.dependencias.cache").value("fora"));
	}

	@Test
	void semKafkaEstaForaDoAr() throws Exception {
		when(kafkaSaude.noAr()).thenReturn(false);

		mvc.perform(get("/health"))
			.andExpect(status().isServiceUnavailable())
			.andExpect(jsonPath("$.status").value("fora do ar"))
			.andExpect(jsonPath("$.dependencias.kafka").value("fora"));
	}

	// ---------- cache da vitrine ----------

	@Test
	void vitrinePrimeiroMissDepoisHitComOMesmoConteudo() throws Exception {
		ligarCache();

		String primeira = mvc.perform(get("/produtos?categoria=livros"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Cache", "MISS"))
			.andReturn().getResponse().getContentAsString();
		String segunda = mvc.perform(get("/produtos?categoria=livros"))
			.andExpect(header().string("X-Cache", "HIT"))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andReturn().getResponse().getContentAsString();

		assertThat(segunda).isEqualTo(primeira);
		// Outro filtro é outra chave
		mvc.perform(get("/produtos?categoria=roupas")).andExpect(header().string("X-Cache", "MISS"));
		// "Livros " e "livros" são a mesma busca
		mvc.perform(get("/produtos?categoria=livros ")).andExpect(header().string("X-Cache", "HIT"));
	}

	@Test
	void adminMudaOPrecoEOProximoGetJaMostraONovo() throws Exception {
		ligarCache();
		String id = criarProduto(skuNovo());
		mvc.perform(get("/produtos/" + id)).andExpect(header().string("X-Cache", "MISS"));
		mvc.perform(get("/produtos/" + id)).andExpect(header().string("X-Cache", "HIT"))
			.andExpect(jsonPath("$.preco").value(199.90));

		mvc.perform(put("/produtos/" + id).contentType(MediaType.APPLICATION_JSON).content("""
				{"sku":"%s","nome":"Livro de teste","categoria":"livros","preco":149.90}"""
			.formatted(JsonPath.<String>read(mvc.perform(get("/produtos/" + id)).andReturn().getResponse()
				.getContentAsString(), "$.sku"))))
			.andExpect(status().isOk());

		// A escrita trocou a versão: a chave velha não é mais procurada
		mvc.perform(get("/produtos/" + id))
			.andExpect(header().string("X-Cache", "MISS"))
			.andExpect(jsonPath("$.preco").value(149.90));
	}

	@Test
	void estoqueAjustadoECategoriaNovaInvalidamAVitrine() throws Exception {
		ligarCache();
		String id = criarProduto(skuNovo());
		mvc.perform(get("/categorias")).andExpect(header().string("X-Cache", "MISS"));
		mvc.perform(get("/categorias")).andExpect(header().string("X-Cache", "HIT"));

		mvc.perform(patch("/produtos/" + id + "/estoque").contentType(MediaType.APPLICATION_JSON)
				.content("{\"disponivel\":3}")).andExpect(status().isOk());
		mvc.perform(get("/categorias")).andExpect(header().string("X-Cache", "MISS"));

		String slug = "cat-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(post("/categorias").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Nova\",\"slug\":\"" + slug + "\"}")).andExpect(status().isCreated());
		mvc.perform(get("/categorias"))
			.andExpect(header().string("X-Cache", "MISS"))
			.andExpect(jsonPath("$[*].slug", hasItem(slug)));
	}

	@Test
	void acentoSobreviveAoCache() throws Exception {
		ligarCache();
		String sku = skuNovo();
		String id = JsonPath.read(mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON).content("""
				{"sku":"%s","nome":"Calção Térmico","categoria":"roupas","preco":89.90,
				 "atributos":{"tamanho":"M"},"estoque":{"disponivel":5}}""".formatted(sku)))
			.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(get("/produtos/" + id)).andExpect(header().string("X-Cache", "MISS"));
		mvc.perform(get("/produtos/" + id))
			.andExpect(header().string("X-Cache", "HIT"))
			.andExpect(jsonPath("$.nome").value("Calção Térmico"));
	}

	@Test
	void produtoQueNaoExisteNaoEhGuardado() throws Exception {
		ligarCache();
		mvc.perform(get("/produtos/nao-existe")).andExpect(status().isNotFound());
		mvc.perform(get("/produtos/nao-existe")).andExpect(status().isNotFound());
	}

	@Test
	void redisForaAVitrineContinuaComOff() throws Exception {
		mvc.perform(get("/produtos"))
			.andExpect(status().isOk())
			.andExpect(header().string("X-Cache", "OFF"))
			.andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(10))));
		mvc.perform(get("/categorias")).andExpect(status().isOk()).andExpect(header().string("X-Cache", "OFF"));
	}

	@Test
	void checkoutPorIdsNaoPassaPeloCache() throws Exception {
		ligarCache();
		String id = criarProduto(skuNovo());

		mvc.perform(get("/produtos?ids=" + id))
			.andExpect(status().isOk())
			.andExpect(header().doesNotExist("X-Cache"))
			.andExpect(jsonPath("$[0].id").value(id));
	}

	private void ligarCache() {
		cacheStore.ligar();
		// O /health confere o cache e tira a pausa de 5 s de quando ele estava "fora"
		vitrineCache.noAr();
	}

	private static String skuNovo() {
		return "TST-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String produtoJson(String sku) {
		return """
				{"sku":"%s","nome":"Livro de teste","categoria":"livros","preco":199.90,
				 "atributos":{"autor":"Fulano","paginas":100},"estoque":{"disponivel":7}}""".formatted(sku);
	}

	private String criarProduto(String sku) throws Exception {
		String resposta = mvc.perform(post("/produtos").contentType(MediaType.APPLICATION_JSON)
				.content(produtoJson(sku)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.id");
	}

}
