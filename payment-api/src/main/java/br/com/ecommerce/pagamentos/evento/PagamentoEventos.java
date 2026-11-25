package br.com.ecommerce.pagamentos.evento;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * O que o pagamentos lê (estoque.reservado, pedido.cancelado) e o que publica no tópico pagamentos.
 * Tudo que precisa vem no evento: valor, forma, cartão final, e-mail. Nenhuma consulta a outro
 * serviço. Quem escuta pagamento.* também não precisa consultar ninguém (o e-mail vai junto).
 */
public final class PagamentoEventos {

	public static final String APROVADO = "pagamento.aprovado";
	public static final String RECUSADO = "pagamento.recusado";
	public static final String ESTORNADO = "pagamento.estornado";

	private PagamentoEventos() {
	}

	// ---------- lidos ----------

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record EstoqueReservado(String usuarioId, String clienteEmail, BigDecimal valorTotal,
			String formaPagamento, String cartaoFinal) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record PedidoCancelado(String usuarioId, String clienteEmail, BigDecimal valorTotal, String formaPagamento,
			String cartaoFinal, String motivo) {
	}

	// ---------- publicados ----------

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Aprovado(String usuarioId, String clienteEmail, BigDecimal valor, String codigoTransacao,
			int tentativas) {
	}

	// motivo: "saldo insuficiente" (o banco disse não) ou "banco indisponível (...)" (não respondeu)
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Recusado(String usuarioId, String clienteEmail, BigDecimal valor, String motivo, int tentativas) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Estornado(String usuarioId, String clienteEmail, BigDecimal valor, String codigoTransacao) {
	}

}
