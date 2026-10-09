package br.com.ecommerce.usuarios.exception;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

// Formato único de erro: { "erro": "...", "detalhes": [...] }
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResponse(String erro, List<String> detalhes) {

	public ErroResponse(String erro) {
		this(erro, List.of());
	}

}
