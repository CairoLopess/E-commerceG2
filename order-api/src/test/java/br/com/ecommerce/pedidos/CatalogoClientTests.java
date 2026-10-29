package br.com.ecommerce.pedidos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import br.com.ecommerce.pedidos.catalogo.CatalogoClient;
import br.com.ecommerce.pedidos.catalogo.CatalogoIndisponivelException;
import br.com.ecommerce.pedidos.catalogo.ProdutoCatalogo;

/**
 * O cliente HTTP de verdade contra um "catálogo" de mentira (servidor HTTP do próprio JDK):
 * resposta normal, erro, lentidão (timeout) e fora do ar.
 */
class CatalogoClientTests {

	static final String RESPOSTA = """
			[{"id":"a","sku":"LIV-1","nome":"Livro","categoria":"livros","preco":119.90,"ativo":true,
			  "atributos":{"autor":"Fulano"},"estoque":{"disponivel":10,"reservado":0}},
			 {"id":"b","sku":"CEL-1","nome":"Celular","categoria":"celulares","preco":1999.90,"ativo":true,
			  "atributos":{},"estoque":{"disponivel":2,"reservado":0}}]""";

	HttpServer servidor;
	String url;
	final AtomicReference<String> queryRecebida = new AtomicReference<>();
	final AtomicReference<String> requestIdRecebido = new AtomicReference<>();

	@BeforeEach
	void subirServidor() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.start();
		url = "http://127.0.0.1:" + servidor.getAddress().getPort();
	}

	@AfterEach
	void pararServidor() {
		servidor.stop(0);
	}

	@Test
	void buscaTodosOsIdsNumaChamadaSoERepassaOXRequestId() {
		responder(200, RESPOSTA, 0);

		Map<String, ProdutoCatalogo> produtos = cliente(Duration.ofSeconds(3)).buscarProdutos(List.of("a", "b"), "req-123");

		assertThat(queryRecebida.get()).isEqualTo("ids=a,b");
		assertThat(requestIdRecebido.get()).isEqualTo("req-123");
		assertThat(produtos).containsOnlyKeys("a", "b");
		assertThat(produtos.get("a").nome()).isEqualTo("Livro");
		assertThat(produtos.get("a").preco()).isEqualByComparingTo("119.90");
		assertThat(produtos.get("b").temEstoque(2)).isTrue();
		assertThat(produtos.get("b").temEstoque(3)).isFalse();
	}

	@Test
	void erroDoCatalogoViraIndisponivel() {
		responder(500, "{\"erro\":\"Erro interno.\"}", 0);

		assertThatThrownBy(() -> cliente(Duration.ofSeconds(3)).buscarProdutos(List.of("a"), null))
			.isInstanceOf(CatalogoIndisponivelException.class);
	}

	@Test
	void catalogoLentoEstouraOTimeoutEmVezDeTravarOCheckout() {
		responder(200, RESPOSTA, 5_000);

		long inicio = System.nanoTime();
		assertThatThrownBy(() -> cliente(Duration.ofMillis(300)).buscarProdutos(List.of("a"), null))
			.isInstanceOf(CatalogoIndisponivelException.class);
		assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
	}

	@Test
	void catalogoForaDoArViraIndisponivel() {
		servidor.stop(0); // ninguém escutando na porta

		assertThatThrownBy(() -> cliente(Duration.ofSeconds(1)).buscarProdutos(List.of("a"), null))
			.isInstanceOf(CatalogoIndisponivelException.class);
	}

	private CatalogoClient cliente(Duration timeout) {
		return new CatalogoClient(url, timeout);
	}

	private void responder(int status, String corpo, long atrasoMs) {
		servidor.createContext("/produtos", troca -> {
			queryRecebida.set(troca.getRequestURI().getRawQuery());
			requestIdRecebido.set(troca.getRequestHeaders().getFirst("X-Request-Id"));
			try {
				Thread.sleep(atrasoMs);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
			troca.getResponseHeaders().set("Content-Type", "application/json");
			troca.sendResponseHeaders(status, bytes.length);
			troca.getResponseBody().write(bytes);
			troca.close();
		});
	}

}
