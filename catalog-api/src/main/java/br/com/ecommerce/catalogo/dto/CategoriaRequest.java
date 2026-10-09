package br.com.ecommerce.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CategoriaRequest(

		@NotBlank(message = "nome é obrigatório")
		String nome,

		@NotBlank(message = "slug é obrigatório")
		@Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*",
				message = "slug deve ter só letras minúsculas, números e hífens (ex.: eletronicos-casa)")
		String slug) {
}
