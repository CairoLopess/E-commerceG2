package br.com.ecommerce.usuarios.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// PUT substitui os dados; a senha é opcional (null mantém a atual)
public record UsuarioUpdateRequest(

		@NotBlank(message = "nome é obrigatório")
		String nome,

		@NotBlank(message = "email é obrigatório")
		@Email(message = "email inválido")
		String email,

		@Size(min = 6, message = "senha deve ter pelo menos 6 caracteres")
		String senha) {
}
