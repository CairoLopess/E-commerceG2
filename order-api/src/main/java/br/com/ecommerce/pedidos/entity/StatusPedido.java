package br.com.ecommerce.pedidos.entity;

/**
 * Cada seta é um evento, e só vale a partir do estado de origem:
 * AGUARDANDO_ESTOQUE --estoque.reservado--> AGUARDANDO_PAGAMENTO --pagamento.aprovado--> PAGO
 * AGUARDANDO_ESTOQUE --estoque.insuficiente--> CANCELADO
 * AGUARDANDO_ESTOQUE ou AGUARDANDO_PAGAMENTO --pagamento.recusado--> CANCELADO
 * AGUARDANDO_ESTOQUE ou AGUARDANDO_PAGAMENTO --cliente cancela--> CANCELADO (publica pedido.cancelado)
 * PAGO e CANCELADO são finais: um CANCELADO não volta a PAGO porque o banco aprovou atrasado.
 */
public enum StatusPedido {

	AGUARDANDO_ESTOQUE,
	AGUARDANDO_PAGAMENTO,
	PAGO,
	CANCELADO;

	// Depois de pago (ou já cancelado), o cliente não cancela mais
	public boolean podeCancelar() {
		return this == AGUARDANDO_ESTOQUE || this == AGUARDANDO_PAGAMENTO;
	}

}
