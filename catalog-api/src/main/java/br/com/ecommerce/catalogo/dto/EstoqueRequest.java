package br.com.ecommerce.catalogo.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

// PATCH /produtos/{id}/estoque: { "disponivel": 25 } define o novo valor (não é soma)
public record EstoqueRequest(

		@NotNull(message = "disponivel é obrigatório")
		@PositiveOrZero(message = "estoque não pode ser negativo")
		Integer disponivel) {
}
