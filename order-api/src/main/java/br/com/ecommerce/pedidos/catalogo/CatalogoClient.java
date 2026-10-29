package br.com.ecommerce.pedidos.catalogo;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import br.com.ecommerce.pedidos.security.UsuarioLogado;

/**
 * Chamada síncrona ao catálogo, direto pela rede interna (http://catalogo:3000), sem passar
 * pelo gateway: o gateway é a porta de fora.
 *
 * Acoplamento temporal: para vender, o catálogo precisa estar no ar naquele instante.
 * O timeout garante que um catálogo travado não trava o checkout junto.
 */
@Component
public class CatalogoClient {

	private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);

	private final RestClient restClient;

	public CatalogoClient(@Value("${catalogo.url}") String url, @Value("${catalogo.timeout}") Duration timeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		this.restClient = RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
	}

	/**
	 * Uma chamada só para todos os produtos (?ids=a,b,c), não uma por produto.
	 * Devolve os produtos ativos por id; id inexistente ou desativado fica de fora do mapa.
	 */
	public Map<String, ProdutoCatalogo> buscarProdutos(Collection<String> ids, String requestId) {
		try {
			ProdutoCatalogo[] produtos = restClient.get()
					.uri(uri -> uri.path("/produtos").queryParam("ids", String.join(",", ids)).build())
					.headers(headers -> {
						if (requestId != null) {
							headers.set(UsuarioLogado.REQUEST_ID, requestId);
						}
					})
					.retrieve()
					.body(ProdutoCatalogo[].class);
			return Arrays.stream(produtos == null ? new ProdutoCatalogo[0] : produtos)
					.collect(Collectors.toMap(ProdutoCatalogo::id, Function.identity()));
		} catch (RestClientException ex) {
			// Fora do ar, timeout ou status de erro: o motivo fica no log, o cliente recebe 503
			log.warn("Catálogo indisponível [{}={}]: {}", UsuarioLogado.REQUEST_ID, requestId, ex.getMessage());
			throw new CatalogoIndisponivelException();
		}
	}

}
