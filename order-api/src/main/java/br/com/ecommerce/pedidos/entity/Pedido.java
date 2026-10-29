package br.com.ecommerce.pedidos.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Tabela pedidos. usuario_id sem FK (o usuário mora no banco do usuarios); itens_pedido com FK
 * para cá, porque está no mesmo banco.
 */
@Entity
@Table(name = "pedidos", indexes = @Index(name = "ix_pedidos_usuario", columnList = "usuario_id"))
public class Pedido {

	@Id
	private UUID id;

	@Column(nullable = false)
	private UUID usuarioId;

	@Column(nullable = false)
	private String clienteEmail;

	// EnumType.STRING: o Hibernate cria o CHECK (status IN (...)) com os 4 estados
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private StatusPedido status;

	@Column(nullable = false, precision = 12, scale = 2)
	private BigDecimal valorTotal = BigDecimal.ZERO;

	// JSONB no Postgres
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private EnderecoEntrega enderecoEntrega;

	@Column(nullable = false)
	private String formaPagamento;

	// Só os 4 últimos dígitos: o número inteiro do cartão nunca é gravado
	@Column(length = 4)
	private String cartaoFinal;

	@Column(nullable = false)
	private Instant criadoEm;

	@ElementCollection
	@CollectionTable(name = "itens_pedido", joinColumns = @JoinColumn(name = "pedido_id"))
	private List<ItemPedido> itens = new ArrayList<>();

	protected Pedido() {
	}

	public Pedido(UUID usuarioId, String clienteEmail, EnderecoEntrega enderecoEntrega, String formaPagamento,
			String cartaoFinal) {
		this.id = UUID.randomUUID();
		this.usuarioId = usuarioId;
		this.clienteEmail = clienteEmail;
		this.status = StatusPedido.AGUARDANDO_ESTOQUE;
		this.enderecoEntrega = enderecoEntrega;
		this.formaPagamento = formaPagamento;
		this.cartaoFinal = cartaoFinal;
		this.criadoEm = Instant.now();
	}

	public void adicionarItem(ItemPedido item) {
		itens.add(item);
		valorTotal = valorTotal.add(item.subtotal());
	}

	public void cancelar() {
		this.status = StatusPedido.CANCELADO;
	}

	public UUID getId() {
		return id;
	}

	public UUID getUsuarioId() {
		return usuarioId;
	}

	public String getClienteEmail() {
		return clienteEmail;
	}

	public StatusPedido getStatus() {
		return status;
	}

	public BigDecimal getValorTotal() {
		return valorTotal;
	}

	public EnderecoEntrega getEnderecoEntrega() {
		return enderecoEntrega;
	}

	public String getFormaPagamento() {
		return formaPagamento;
	}

	public String getCartaoFinal() {
		return cartaoFinal;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public List<ItemPedido> getItens() {
		return itens;
	}

}
