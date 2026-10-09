package br.com.ecommerce.usuarios.exception;

import org.springframework.http.HttpStatus;

// Erro de regra de negócio que vira resposta HTTP com o status e a mensagem dados
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	public ApiException(HttpStatus status, String mensagem) {
		super(mensagem);
		this.status = status;
	}

	public HttpStatus getStatus() {
		return status;
	}

	public static ApiException naoEncontrado(String mensagem) {
		return new ApiException(HttpStatus.NOT_FOUND, mensagem);
	}

	public static ApiException conflito(String mensagem) {
		return new ApiException(HttpStatus.CONFLICT, mensagem);
	}

	public static ApiException naoAutenticado(String mensagem) {
		return new ApiException(HttpStatus.UNAUTHORIZED, mensagem);
	}

}
