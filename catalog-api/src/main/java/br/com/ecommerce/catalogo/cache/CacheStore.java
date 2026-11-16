package br.com.ecommerce.catalogo.cache;

import java.time.Duration;

/**
 * As quatro operações que o cache da vitrine usa. Em produção é o Redis (RedisCacheStore); nos
 * testes, um mapa em memória. Qualquer falha (Redis fora, timeout) sai como exceção: quem decide o
 * que fazer é a VitrineCache (ir direto ao banco).
 */
public interface CacheStore {

	// null se a chave não existe (ou expirou)
	String get(String chave);

	void set(String chave, String valor, Duration ttl);

	// INCR: cria com 1 se não existir. Devolve o valor novo.
	long incrementar(String chave);

	// Lança exceção se o cache não responder
	void ping();

}
