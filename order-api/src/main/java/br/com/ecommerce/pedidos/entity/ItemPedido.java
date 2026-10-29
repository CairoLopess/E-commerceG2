package br.com.ecommerce.pedidos.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Linha da tabela itens_pedido. Nome e preço são copiados do catálogo na hora da compra:
 * se o produto mudar de preço amanhã, o pedido de hoje não muda. Não é duplicação, é histórico.
 */
@Embeddable
public class ItemPedido {

	@Column(nullable = false)
	private String produtoId;

	@Column(nullable = false)
	private String nomeProduto;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal precoUnitario;

	@Column(nullable = false)
	private int quantidade;

	protected ItemPedido() {
	}

	public ItemPedido(String produtoId, String nomeProduto, BigDecimal precoUnitario, int quantidade) {
		this.produtoId = produtoId;
		this.nomeProduto = nomeProduto;
		this.precoUnitario = precoUnitario;
		this.quantidade = quantidade;
	}

	public BigDecimal subtotal() {
		return precoUnitario.multiply(BigDecimal.valueOf(quantidade));
	}

	public String getProdutoId() {
		return produtoId;
	}

	public String getNomeProduto() {
		return nomeProduto;
	}

	public BigDecimal getPrecoUnitario() {
		return precoUnitario;
	}

	public int getQuantidade() {
		return quantidade;
	}

}
