package br.com.ecommerce.catalogo.document;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Coleção reservas: uma por pedido, com os itens. O índice único em pedidoId é a idempotência:
 * o mesmo pedido.criado entregue duas vezes não reserva duas vezes (o 2º insert dá chave
 * duplicada). O status guarda o resultado, para republicá-lo se o evento vier de novo.
 */
@Document("reservas")
public class Reserva {

	public enum Status {
		PROCESSANDO, RESERVADO, INSUFICIENTE
	}

	public record Item(String produtoId, int quantidade) {
	}

	@Id
	private String id;

	@Indexed(unique = true)
	private String pedidoId;

	private List<Item> itens;

	private Status status = Status.PROCESSANDO;

	private String motivo;

	private Instant criadoEm;

	protected Reserva() {
	}

	public Reserva(String pedidoId, List<Item> itens) {
		this.pedidoId = pedidoId;
		this.itens = itens;
		this.criadoEm = Instant.now();
	}

	public void reservado() {
		this.status = Status.RESERVADO;
	}

	public void insuficiente(String motivo) {
		this.status = Status.INSUFICIENTE;
		this.motivo = motivo;
	}

	public String getId() {
		return id;
	}

	public String getPedidoId() {
		return pedidoId;
	}

	public List<Item> getItens() {
		return itens;
	}

	public Status getStatus() {
		return status;
	}

	public String getMotivo() {
		return motivo;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
