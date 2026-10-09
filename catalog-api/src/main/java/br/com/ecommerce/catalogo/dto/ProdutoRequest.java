package br.com.ecommerce.catalogo.dto;

import java.math.BigDecimal;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// Usado no POST (cria) e no PUT (substitui). Estoque e ativo são opcionais.
public record ProdutoRequest(

		@NotBlank(message = "sku é obrigatório")
		String sku,

		@NotBlank(message = "nome é obrigatório")
		String nome,

		@NotBlank(message = "categoria é obrigatória")
		String categoria,

		@NotNull(message = "preco é obrigatório")
		@DecimalMin(value = "0.00", inclusive = false, message = "preco deve ser maior que zero")
		@Digits(integer = 10, fraction = 2, message = "preco deve ter no máximo 2 casas decimais")
		BigDecimal preco,

		Map<String, Object> atributos,

		@Valid
		EstoqueRequest estoque,

		Boolean ativo) {
}
