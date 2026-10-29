package br.com.ecommerce.pedidos.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CarrinhoItemRequest(

		@NotBlank(message = "produtoId é obrigatório")
		String produtoId,

		@NotNull(message = "quantidade é obrigatória")
		@Min(value = 1, message = "quantidade deve ser maior que zero")
		Integer quantidade) {
}
