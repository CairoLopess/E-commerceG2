package br.com.ecommerce.catalogo.evento;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * O que o catálogo lê do pedido.criado (publicado pelo pedidos). Campos que ele não conhece são
 * ignorados: o pedidos pode acrescentar campos sem quebrar o catálogo.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PedidoCriado(String usuarioId, String clienteEmail, BigDecimal valorTotal, String formaPagamento,
		String cartaoFinal, List<Item> itens) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String produtoId, String nome, int quantidade) {
	}

}
