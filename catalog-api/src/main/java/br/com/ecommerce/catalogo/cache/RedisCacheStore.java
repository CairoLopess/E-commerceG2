package br.com.ecommerce.catalogo.cache;

import java.time.Duration;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

// GET, SET com EX (TTL), INCR e PING no Redis. Os timeouts curtos estão no application.properties.
@Component
public class RedisCacheStore implements CacheStore {

	private final StringRedisTemplate redis;

	public RedisCacheStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public String get(String chave) {
		return redis.opsForValue().get(chave);
	}

	@Override
	public void set(String chave, String valor, Duration ttl) {
		redis.opsForValue().set(chave, valor, ttl);
	}

	@Override
	public long incrementar(String chave) {
		Long valor = redis.opsForValue().increment(chave);
		return valor == null ? 0 : valor;
	}

	@Override
	public void ping() {
		redis.execute((RedisCallback<String>) conexao -> conexao.ping());
	}

}
