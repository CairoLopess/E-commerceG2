package br.com.ecommerce.catalogo.cache;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Cache-aside da vitrine (GET /produtos, /produtos/{id}, /categorias), com invalidação por versão.
 *
 *   chave: catalogo:v{versao}:{recurso}   valor: o JSON inteiro que a rota devolve   TTL: 60 s
 *
 *   HIT   achou no Redis: devolve sem ir ao Mongo
 *   MISS  não achou: busca no Mongo, guarda e devolve
 *   OFF   Redis fora: vai direto ao Mongo. O cache acelera, não sustenta.
 *
 * Invalidar = INCR catalogo:versao. Nenhuma lista é editada ou apagada: as chaves da versão velha
 * param de ser procuradas e vencem sozinhas pelo TTL. Com uma chave por filtro da vitrine, é o jeito
 * de apagar todas de uma vez com um comando.
 *
 * Com o Redis fora, as próximas requisições pulam o Redis por 5 s, em vez de cada uma esperar o
 * timeout: a vitrine fica tão rápida quanto o Mongo, só sem cache.
 */
@Component
public class VitrineCache {

	public static final String VERSAO = "catalogo:versao";
	public static final String CABECALHO = "X-Cache";

	private static final long PAUSA_MS = 5000;
	// Buscas maiores que isso não viram chave (ninguém repete uma busca de 200 caracteres)
	private static final int RECURSO_MAX = 150;

	private static final Logger log = LoggerFactory.getLogger(VitrineCache.class);

	public enum Origem {
		HIT, MISS, OFF
	}

	public record Resposta(String json, Origem origem) {

		// O JSON já pronto, com o X-Cache dizendo de onde veio
		public ResponseEntity<String> http() {
			return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).header(CABECALHO, origem.name())
					.body(json);
		}

	}

	private final CacheStore store;
	private final JsonMapper json;
	private final Duration ttl;
	private volatile long pausadoAte;

	public VitrineCache(CacheStore store, JsonMapper json, @Value("${catalogo.cache.ttl}") Duration ttl) {
		this.store = store;
		this.json = json;
		this.ttl = ttl;
	}

	public Resposta lerOuBuscar(String recurso, Supplier<?> buscar) {
		if (recurso.length() > RECURSO_MAX || System.currentTimeMillis() < pausadoAte) {
			return new Resposta(json.writeValueAsString(buscar.get()), Origem.OFF);
		}
		String chave;
		try {
			chave = "catalogo:v" + versao() + ":" + recurso;
			String guardado = store.get(chave);
			if (guardado != null) {
				return new Resposta(guardado, Origem.HIT);
			}
		} catch (RuntimeException e) {
			fora(e);
			return new Resposta(json.writeValueAsString(buscar.get()), Origem.OFF);
		}
		// MISS: um erro da busca (ex.: 404) sobe e não é guardado
		String valor = json.writeValueAsString(buscar.get());
		try {
			store.set(chave, valor, ttlComVariacao());
		} catch (RuntimeException e) {
			fora(e);
		}
		return new Resposta(valor, Origem.MISS);
	}

	/**
	 * Toda escrita de produto, categoria ou estoque chama aqui. Sempre tenta, mesmo com o Redis
	 * marcado como fora: se não der, o TTL limita quanto tempo alguém vê o dado velho.
	 */
	public void invalidar(String motivo) {
		try {
			long versao = store.incrementar(VERSAO);
			log.info("Cache da vitrine invalidado ({}): versão {}", motivo, versao);
		} catch (RuntimeException e) {
			fora(e);
			log.warn("Cache da vitrine NÃO invalidado ({}): o dado velho some em até {} s (TTL)", motivo,
					ttl.toSeconds());
		}
	}

	// Para o /health: cache fora deixa o catálogo degradado, nunca fora do ar
	public boolean noAr() {
		try {
			store.ping();
			if (pausadoAte != 0) {
				pausadoAte = 0;
				log.info("Cache da vitrine de volta");
			}
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}

	private String versao() {
		String versao = store.get(VERSAO);
		return versao == null ? "0" : versao;
	}

	// Variação de até 10%: chaves criadas juntas não vencem todas no mesmo segundo (avalanche)
	private Duration ttlComVariacao() {
		long ms = ttl.toMillis();
		return Duration.ofMillis(ms + ThreadLocalRandom.current().nextLong(ms / 10 + 1));
	}

	private void fora(RuntimeException e) {
		if (pausadoAte == 0 || System.currentTimeMillis() >= pausadoAte) {
			log.warn("Cache da vitrine fora ({}): indo direto ao banco (X-Cache: OFF)", e.getClass().getSimpleName());
		}
		pausadoAte = System.currentTimeMillis() + PAUSA_MS;
	}

}
