package br.com.ecommerce.pedidos.entity;

/**
 * O pedido já nasce com os 4 estados. Nesta aula ele fica parado em AGUARDANDO_ESTOQUE;
 * quem o faz andar é o Kafka, nas próximas aulas (estoque reservado, pagamento aprovado).
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
