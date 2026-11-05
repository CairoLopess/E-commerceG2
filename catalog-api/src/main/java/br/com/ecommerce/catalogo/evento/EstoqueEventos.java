package br.com.ecommerce.catalogo.evento;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Os dados dos eventos que o catálogo publica no tópico estoque. Repassam o que veio no
 * pedido.criado: quem escuta (pedidos, notificações e, na próxima aula, pagamentos) trabalha só
 * com o evento, sem consultar ninguém.
 */
public final class EstoqueEventos {

	public static final String RESERVADO = "estoque.reservado";
	public static final String INSUFICIENTE = "estoque.insuficiente";

	private EstoqueEventos() {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Reservado(String usuarioId, String clienteEmail, BigDecimal valorTotal, String formaPagamento,
			String cartaoFinal) {

		public static Reservado de(PedidoCriado pedido) {
			return new Reservado(pedido.usuarioId(), pedido.clienteEmail(), pedido.valorTotal(),
					pedido.formaPagamento(), pedido.cartaoFinal());
		}

	}

	public record Insuficiente(String motivo, String usuarioId, String clienteEmail) {

		public static Insuficiente de(PedidoCriado pedido, String motivo) {
			return new Insuficiente(motivo, pedido.usuarioId(), pedido.clienteEmail());
		}

	}

}
