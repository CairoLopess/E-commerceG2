package br.com.ecommerce.pagamentos.banco;

/**
 * O banco respondeu "não" (402 saldo insuficiente, ou outro 4xx). É uma resposta: insistir não muda
 * o "não", então não tem retry. Para o circuit breaker, é sucesso (o banco está de pé).
 */
public class BancoRecusouException extends RuntimeException {

	public BancoRecusouException(String motivo) {
		super(motivo);
	}

}
