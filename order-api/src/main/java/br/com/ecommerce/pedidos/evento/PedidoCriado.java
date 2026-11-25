package br.com.ecommerce.pedidos.evento;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.ecommerce.pedidos.entity.ItemPedido;
import br.com.ecommerce.pedidos.entity.Pedido;

/**
 * Os dados do pedido.criado. Leva o que os outros precisam para trabalhar sem consultar o
 * pedidos: os itens para o catálogo reservar, o e-mail para as notificações avisarem, o valor e a
 * forma de pagamento para o pagamento (próxima aula). Do cartão, só os 4 últimos dígitos: o
 * evento fica guardado no tópico e muita gente lê.
 *
 * Evento é contrato: só acrescente campos. Renomear ou remover quebra quem já escuta.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoCriado(UUID usuarioId, String clienteEmail, BigDecimal valorTotal, String formaPagamento,
		String cartaoFinal, List<Item> itens) {

	public record Item(String produtoId, String nome, BigDecimal precoUnitario, int quantidade) {

		static Item from(ItemPedido item) {
			return new Item(item.getProdutoId(), item.getNomeProduto(), item.getPrecoUnitario(), item.getQuantidade());
		}

	}

	public static PedidoCriado from(Pedido pedido) {
		return new PedidoCriado(pedido.getUsuarioId(), pedido.getClienteEmail(), pedido.getValorTotal(),
				pedido.getFormaPagamento(), pedido.getCartaoFinal(),
				pedido.getItens().stream().map(Item::from).toList());
	}

}
