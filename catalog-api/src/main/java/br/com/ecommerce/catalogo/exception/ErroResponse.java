package br.com.ecommerce.catalogo.exception;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Formato único de erro, igual ao do serviço usuarios: { "erro": "...", "detalhes": [...] }
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResponse(String erro, List<String> detalhes) {

	public ErroResponse(String erro) {
		this(erro, List.of());
	}

}
