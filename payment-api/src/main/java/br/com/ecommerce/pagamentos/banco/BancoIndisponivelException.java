package br.com.ecommerce.pagamentos.banco;

/**
 * O banco não respondeu: timeout, conexão recusada, 5xx ou 429. Falha passageira: vale tentar de
 * novo, e conta como falha para o circuit breaker.
 *
 * timeout = true quer dizer "não sei o que aconteceu": o banco pode ter cobrado e só a resposta
 * não chegou a tempo.
 */
public class BancoIndisponivelException extends RuntimeException {

	private final boolean timeout;

	public BancoIndisponivelException(String motivo, boolean timeout, Throwable causa) {
		super(motivo, causa);
		this.timeout = timeout;
	}

	public boolean isTimeout() {
		return timeout;
	}

}
