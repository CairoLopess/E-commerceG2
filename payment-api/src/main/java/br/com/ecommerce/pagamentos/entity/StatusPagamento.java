package br.com.ecommerce.pagamentos.entity;

/**
 * PENDENTE   gravado antes de chamar o banco
 * APROVADO   o banco cobrou
 * RECUSADO   o banco respondeu "não" (402 saldo insuficiente)
 * ERRO       o banco não respondeu (timeout, 503, circuito aberto): tratado como recusado
 * CANCELADO  o pedido foi cancelado antes da cobrança: ocupa o pedido_id e impede a cobrança
 * ESTORNADO  cobrado e devolvido
 */
public enum StatusPagamento {

	PENDENTE, APROVADO, RECUSADO, ERRO, CANCELADO, ESTORNADO

}
