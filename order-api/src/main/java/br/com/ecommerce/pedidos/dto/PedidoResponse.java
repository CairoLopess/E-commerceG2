package br.com.ecommerce.pedidos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.ecommerce.pedidos.entity.EnderecoEntrega;
import br.com.ecommerce.pedidos.entity.ItemPedido;
import br.com.ecommerce.pedidos.entity.Pedido;
import br.com.ecommerce.pedidos.entity.StatusPedido;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoResponse(UUID id, StatusPedido status, BigDecimal valorTotal, String clienteEmail,
		EnderecoEntrega enderecoEntrega, String formaPagamento, String cartaoFinal, Instant criadoEm,
		List<Item> itens) {

	public record Item(String produtoId, String nome, BigDecimal precoUnitario, int quantidade, BigDecimal subtotal) {

		static Item from(ItemPedido item) {
			return new Item(item.getProdutoId(), item.getNomeProduto(), item.getPrecoUnitario(), item.getQuantidade(),
					item.subtotal());
		}

	}

	public static PedidoResponse from(Pedido pedido) {
		return new PedidoResponse(pedido.getId(), pedido.getStatus(), pedido.getValorTotal(), pedido.getClienteEmail(),
				pedido.getEnderecoEntrega(), pedido.getFormaPagamento(), pedido.getCartaoFinal(), pedido.getCriadoEm(),
				pedido.getItens().stream().map(Item::from).toList());
	}

}
