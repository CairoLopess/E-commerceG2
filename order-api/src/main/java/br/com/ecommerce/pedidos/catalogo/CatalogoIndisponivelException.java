package br.com.ecommerce.pedidos.catalogo;

import org.springframework.http.HttpStatus;

import br.com.ecommerce.pedidos.exception.ApiException;

// Catálogo fora do ar, lento demais (timeout) ou respondendo erro: vira 503 para o cliente
public class CatalogoIndisponivelException extends ApiException {

	public CatalogoIndisponivelException() {
		super(HttpStatus.SERVICE_UNAVAILABLE, "Catálogo indisponível no momento. Tente de novo em instantes.");
	}

}
