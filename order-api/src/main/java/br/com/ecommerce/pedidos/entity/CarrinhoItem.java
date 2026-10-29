package br.com.ecommerce.pedidos.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Tabela carrinho_itens: chave (usuario_id, produto_id), um produto aparece uma vez por carrinho.
 * Sem FK para usuarios e produtos: eles moram em outros bancos. Quem garante que o produto
 * existe é o código, perguntando ao catálogo.
 */
@Entity
@Table(name = "carrinho_itens", check = @CheckConstraint(name = "ck_carrinho_quantidade", constraint = "quantidade > 0"))
@IdClass(CarrinhoItem.Chave.class)
public class CarrinhoItem {

	@Id
	private UUID usuarioId;

	// id do Mongo do catálogo
	@Id
	private String produtoId;

	@Column(nullable = false)
	private int quantidade;

	protected CarrinhoItem() {
	}

	public CarrinhoItem(UUID usuarioId, String produtoId, int quantidade) {
		this.usuarioId = usuarioId;
		this.produtoId = produtoId;
		this.quantidade = quantidade;
	}

	public void somar(int mais) {
		this.quantidade += mais;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public String getProdutoId() {
		return produtoId;
	}

	public int getQuantidade() {
		return quantidade;
	}

	// Chave composta (exigência do @IdClass: mesmos nomes dos campos @Id, equals e hashCode)
	public static class Chave implements Serializable {

		private UUID usuarioId;
		private String produtoId;

		protected Chave() {
		}

		public Chave(UUID usuarioId, String produtoId) {
			this.usuarioId = usuarioId;
			this.produtoId = produtoId;
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Chave outra && Objects.equals(usuarioId, outra.usuarioId)
					&& Objects.equals(produtoId, outra.produtoId);
		}

		@Override
		public int hashCode() {
			return Objects.hash(usuarioId, produtoId);
		}

	}

}
