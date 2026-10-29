package br.com.ecommerce.pedidos.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Endereço copiado para dentro do pedido (coluna JSONB endereco_entrega). Se o cliente mudar
 * de endereço amanhã, o pedido de hoje continua indo para onde foi pedido.
 * Chega no corpo do checkout: assim o pedidos não precisa chamar o usuarios na hora da compra.
 */
public record EnderecoEntrega(

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
		String uf) {

	public EnderecoEntrega normalizado() {
		return new EnderecoEntrega(cep, logradouro, numero, complemento, cidade, uf.toUpperCase());
	}

}
