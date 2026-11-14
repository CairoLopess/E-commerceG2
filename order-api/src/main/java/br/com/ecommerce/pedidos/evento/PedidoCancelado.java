package br.com.ecommerce.pedidos.evento;

import java.math.BigDecimal;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.ecommerce.pedidos.entity.Pedido;

/**
 * Os dados do pedido.cancelado (o cliente cancelou). Quem reage:
 *   catalogo   devolve o estoque reservado (compensação)
 *   pagamentos estorna o que foi cobrado, ou grava CANCELADO para não cobrar depois
 *
 * Leva o que o pagamentos precisa para gravar a linha sem consultar ninguém (usuário e valor) e o
 * e-mail para avisar do estorno.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PedidoCancelado(UUID usuarioId, String clienteEmail, BigDecimal valorTotal, String formaPagamento,
		String cartaoFinal, String motivo) {

	public static PedidoCancelado from(Pedido pedido, String motivo) {
		return new PedidoCancelado(pedido.getUsuarioId(), pedido.getClienteEmail(), pedido.getValorTotal(),
				pedido.getFormaPagamento(), pedido.getCartaoFinal(), motivo);
	}

}
