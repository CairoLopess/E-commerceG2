package br.com.ecommerce.pagamentos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.ecommerce.pagamentos.banco.BancoClient;
import br.com.ecommerce.pagamentos.banco.BancoIndisponivelException;
import br.com.ecommerce.pagamentos.banco.BancoRecusouException;

/**
 * O BancoClient contra um servidor HTTP de verdade (o do próprio JDK), para conferir como cada
 * resposta vira resultado, inclusive o timeout: um banco que demora mais que o limite.
 */
class BancoClientTests {

	HttpServer servidor;
	volatile int status;
	volatile String corpo;
	volatile long demoraMs;

	BancoClient banco;

	@BeforeEach
	void subirBanco() throws IOException {
		servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		servidor.createContext("/", this::responder);
		servidor.start();
		String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
		banco = new BancoClient(url, Duration.ofMillis(300), Duration.ofSeconds(2));
	}

	@AfterEach
	void derrubarBanco() {
		servidor.stop(0);
	}

	private void responder(HttpExchange troca) throws IOException {
		try {
			Thread.sleep(demoraMs);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		byte[] bytes = corpo == null ? new byte[0] : corpo.getBytes(StandardCharsets.UTF_8);
		troca.getResponseHeaders().add("Content-Type", "application/json");
		troca.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
		if (bytes.length > 0) {
			troca.getResponseBody().write(bytes);
		}
		troca.close();
	}

	@Test
	void aprovadaDevolveOCodigo() {
		responde(201, "{\"aprovado\":true,\"codigo\":\"TX-1234\"}");

		assertThat(banco.cobrar(UUID.randomUUID(), new BigDecimal("60.00"), "pix", null)).isEqualTo("TX-1234");
	}

	@Test
	void recusadaViraRecusaComOMotivoDoBanco() {
		responde(402, "{\"aprovado\":false,\"motivo\":\"saldo insuficiente\"}");

		assertThatThrownBy(() -> banco.cobrar(UUID.randomUUID(), BigDecimal.TEN, "cartao", "0002"))
			.isInstanceOf(BancoRecusouException.class)
			.hasMessage("saldo insuficiente");
	}

	@Test
	void erro503ViraIndisponivelSemTimeout() {
		responde(503, "{\"erro\":\"banco indisponível\"}");

		assertThatThrownBy(() -> banco.cobrar(UUID.randomUUID(), BigDecimal.TEN, "pix", null))
			.isInstanceOfSatisfying(BancoIndisponivelException.class, e -> {
				assertThat(e.getMessage()).isEqualTo("HTTP 503");
				assertThat(e.isTimeout()).isFalse();
			});
	}

	@Test
	void bancoLentoEstouraOTimeout() {
		responde(201, "{\"codigo\":\"TX-TARDE\"}");
		demoraMs = 1000; // o limite é 300 ms

		long inicio = System.nanoTime();
		assertThatThrownBy(() -> banco.cobrar(UUID.randomUUID(), BigDecimal.TEN, "pix", null))
			.isInstanceOfSatisfying(BancoIndisponivelException.class, e -> {
				assertThat(e.getMessage()).isEqualTo("sem resposta em 300 ms (timeout)");
				assertThat(e.isTimeout()).isTrue();
			});
		// Desistiu no limite, não esperou o banco
		assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofMillis(900));
	}

	@Test
	void bancoForaDoArViraSemConexao() {
		servidor.stop(0);

		assertThatThrownBy(() -> banco.cobrar(UUID.randomUUID(), BigDecimal.TEN, "pix", null))
			.isInstanceOfSatisfying(BancoIndisponivelException.class, e -> assertThat(e.isTimeout()).isFalse());
	}

	@Test
	void estornoDevolveTrueOuFalseNo404() {
		responde(201, "{\"codigo\":\"TX-1234\"}");
		assertThat(banco.estornar("TX-1234", UUID.randomUUID())).isTrue();

		responde(404, "{\"erro\":\"cobrança não encontrada\"}");
		assertThat(banco.estornar(null, UUID.randomUUID())).isFalse();
	}

	private void responde(int status, String corpo) {
		this.status = status;
		this.corpo = corpo;
	}

}
