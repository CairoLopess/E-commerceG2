package br.com.ecommerce.pedidos.dto;

import br.com.ecommerce.pedidos.entity.EnderecoEntrega;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// Os itens não vêm no corpo: o checkout fecha o que está no carrinho
public record CheckoutRequest(

		@NotNull(message = "enderecoEntrega é obrigatório")
		@Valid
		EnderecoEntrega enderecoEntrega,

		@NotBlank(message = "formaPagamento é obrigatória")
		@Pattern(regexp = "cartao|pix|boleto", message = "formaPagamento deve ser cartao, pix ou boleto")
		String formaPagamento,

		// Só para cartao. Usado para tirar os 4 últimos dígitos; o número inteiro não é gravado.
		@Pattern(regexp = "\\d{13,19}", message = "numeroCartao deve ter de 13 a 19 dígitos")
		String numeroCartao) {
}
