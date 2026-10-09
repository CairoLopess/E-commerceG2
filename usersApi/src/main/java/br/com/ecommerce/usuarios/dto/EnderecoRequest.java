package br.com.ecommerce.usuarios.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EnderecoRequest(

		String apelido,

		@NotBlank(message = "cep é obrigatório")
		@Pattern(regexp = "\\d{5}-?\\d{3}", message = "cep deve ter o formato 00000-000")
		String cep,

		@NotBlank(message = "logradouro é obrigatório")
		String logradouro,

		String numero,

		String complemento,

		@NotBlank(message = "cidade é obrigatória")
		String cidade,

		@NotBlank(message = "uf é obrigatória")
		@Pattern(regexp = "[A-Za-z]{2}", message = "uf deve ter 2 letras")
		String uf,

		Boolean principal) {
}
