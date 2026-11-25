package br.com.ecommerce.catalogo;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Component;

import br.com.ecommerce.catalogo.cache.CacheStore;

/**
 * O "Redis" dos testes: um mapa em memória (sem TTL de verdade), no lugar do RedisCacheStore.
 * Começa DESLIGADO, como um Redis fora do ar: os testes que não são de cache veem a vitrine sem
 * cache (X-Cache: OFF) e não dependem do que outro teste guardou. Os testes de cache ligam.
 */
@Component
@Primary
public class MemoriaCacheStore implements CacheStore {

	private final Map<String, String> dados = new ConcurrentHashMap<>();
	private volatile boolean noAr;

	public void ligar() {
		dados.clear();
		noAr = true;
	}

	public void desligar() {
		noAr = false;
	}

	@Override
	public String get(String chave) {
		conferir();
		return dados.get(chave);
	}

	@Override
	public void set(String chave, String valor, Duration ttl) {
		conferir();
		dados.put(chave, valor);
	}

	@Override
	public long incrementar(String chave) {
		conferir();
		return Long.parseLong(dados.merge(chave, "1", (atual, um) -> String.valueOf(Long.parseLong(atual) + 1)));
	}

	@Override
	public void ping() {
		conferir();
	}

	private void conferir() {
		if (!noAr) {
			throw new RedisConnectionFailureException("cache de teste desligado");
		}
	}

}
