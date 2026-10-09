package br.com.ecommerce.usuarios.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

		@NotBlank(message = "email é obrigatório")
		String email,

		@NotBlank(message = "senha é obrigatória")
		String senha) {
}
