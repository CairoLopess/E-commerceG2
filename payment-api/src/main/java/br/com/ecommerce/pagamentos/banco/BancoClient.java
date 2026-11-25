package br.com.ecommerce.pagamentos.banco;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A chamada HTTP ao banco do cartão (banco-falso), sem nenhuma insistência: uma chamada, uma
 * resposta. Timeout, retry e circuit breaker ficam em volta, na CobrancaResiliente.
 *
 * Toda resposta vira uma de três coisas:
 *   201                           -> o código da transação (TX-...)
 *   402 e outros 4xx              -> BancoRecusouException (uma resposta: não adianta repetir)
 *   5xx, 429, timeout, sem conexão -> BancoIndisponivelException (passageira: pode dar certo depois)
 */
@Component
public class BancoClient {

	private final RestClient cobrancas;
	private final RestClient estornos;
	private final Duration timeout;

	public BancoClient(@Value("${banco.url}") String url, @Value("${banco.timeout}") Duration timeout,
			@Value("${banco.timeout-estorno}") Duration timeoutEstorno) {
		this.timeout = timeout;
		this.cobrancas = cliente(url, timeout);
		this.estornos = cliente(url, timeoutEstorno);
	}

	// Sem timeout, o HttpURLConnection espera para sempre: toda chamada pela rede precisa de um
	private static RestClient cliente(String url, Duration timeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record CobrancaRequest(BigDecimal valor, String cartaoFinal, String referencia, String forma) {
	}

	record EstornoRequest(String codigo, String referencia) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record RespostaBanco(String codigo, String motivo, String erro) {
	}

	/**
	 * Cobra. A referência é o id do pedido: a chave de idempotência. Repetir a chamada com a mesma
	 * referência devolve a mesma cobrança, sem cobrar de novo. É isso que torna o retry seguro.
	 */
	public String cobrar(UUID referencia, BigDecimal valor, String forma, String cartaoFinal) {
		return chamar(() -> cobrancas.post().uri("/cobrancas")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new CobrancaRequest(valor, cartaoFinal, referencia.toString(), forma))
				.exchange((requisicao, resposta) -> {
					int status = resposta.getStatusCode().value();
					RespostaBanco corpo = ler(resposta);
					if ((status == 200 || status == 201) && corpo != null && corpo.codigo() != null) {
						return corpo.codigo();
					}
					if (status == 200 || status == 201) {
						throw new BancoIndisponivelException("resposta do banco sem código", false, null);
					}
					throw falha(status, corpo);
				}));
	}

	/**
	 * Estorna pela referência (e pelo código, se houver). true = estornado (ou já estava);
	 * false = o banco não tem cobrança aprovada com essa referência (404): nada a devolver.
	 */
	public boolean estornar(String codigo, UUID referencia) {
		return chamar(() -> estornos.post().uri("/estornos")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new EstornoRequest(codigo, referencia.toString()))
				.exchange((requisicao, resposta) -> {
					int status = resposta.getStatusCode().value();
					if (status == 200 || status == 201) {
						return true;
					}
					if (status == 404) {
						return false;
					}
					throw falha(status, ler(resposta));
				}));
	}

	private <T> T chamar(java.util.function.Supplier<T> chamada) {
		try {
			return chamada.get();
		} catch (ResourceAccessException e) {
			// Timeout de leitura: a requisição chegou, o banco pode ter processado. "Não sei."
			if (e.getCause() instanceof SocketTimeoutException semResposta
					&& !String.valueOf(semResposta.getMessage()).toLowerCase().contains("connect")) {
				throw new BancoIndisponivelException("sem resposta em " + timeout.toMillis() + " ms (timeout)", true, e);
			}
			throw new BancoIndisponivelException("sem conexão com o banco", false, e);
		}
	}

	private static RuntimeException falha(int status, RespostaBanco corpo) {
		if (status >= 500 || status == 429) {
			return new BancoIndisponivelException("HTTP " + status, false, null);
		}
		return new BancoRecusouException(corpo != null && corpo.motivo() != null ? corpo.motivo() : "HTTP " + status);
	}

	// 503 pode vir sem corpo (ou com HTML de um proxy): sem corpo legível, segue sem ele
	private static RespostaBanco ler(ConvertibleClientHttpResponse resposta) {
		try {
			return resposta.bodyTo(RespostaBanco.class);
		} catch (RuntimeException e) {
			return null;
		}
	}

}
