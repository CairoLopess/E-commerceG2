package br.com.ecommerce.catalogo.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import br.com.ecommerce.catalogo.document.Produto;
import br.com.ecommerce.catalogo.document.Reserva;
import br.com.ecommerce.catalogo.evento.EstoqueEventos;
import br.com.ecommerce.catalogo.evento.Eventos;
import br.com.ecommerce.catalogo.evento.KafkaConfig;
import br.com.ecommerce.catalogo.evento.PedidoCriado;
import br.com.ecommerce.catalogo.repository.ProdutoRepository;
import br.com.ecommerce.catalogo.repository.ReservaRepository;

/**
 * Reação ao pedido.criado: reserva o estoque de todos os itens, ou de nenhum.
 *
 * 1. Idempotência: grava a reserva antes de tudo; reservas.pedidoId é único, então o mesmo
 *    evento entregue de novo não reserva duas vezes.
 * 2. Tudo ou nada: cada item só sai do disponível se houver (update com $gte). Faltou um, devolve
 *    os que já tinha tirado e publica estoque.insuficiente.
 * 3. Deu tudo certo: publica estoque.reservado.
 */
@Service
public class ReservaService {

	private static final Logger log = LoggerFactory.getLogger(ReservaService.class);

	private final ReservaRepository reservas;
	private final ProdutoRepository produtos;
	private final Eventos eventos;

	public ReservaService(ReservaRepository reservas, ProdutoRepository produtos, Eventos eventos) {
		this.reservas = reservas;
		this.produtos = produtos;
		this.eventos = eventos;
	}

	public void reservar(String pedidoId, PedidoCriado pedido) {
		List<Reserva.Item> itens = pedido.itens().stream()
				.map(item -> new Reserva.Item(item.produtoId(), item.quantidade()))
				.toList();

		// 1. idempotência
		Reserva reserva;
		try {
			reserva = reservas.insert(new Reserva(pedidoId, itens));
		} catch (DuplicateKeyException jaVeio) {
			republicar(pedidoId, pedido);
			return;
		}

		// 2. tudo ou nada
		List<Reserva.Item> reservados = new ArrayList<>();
		for (Reserva.Item item : itens) {
			if (item.quantidade() <= 0 || !produtos.reservar(item.produtoId(), item.quantidade())) {
				// faltou: devolve o que já tirou
				reservados.forEach(r -> produtos.devolverReserva(r.produtoId(), r.quantidade()));
				reserva.insuficiente(motivo(item));
				reservas.save(reserva);
				log.info("Pedido {} sem estoque: {}", pedidoId, reserva.getMotivo());
				publicarResultado(pedidoId, pedido, reserva);
				return;
			}
			reservados.add(item);
		}

		// 3. deu tudo certo
		reserva.reservado();
		reservas.save(reserva);
		log.info("Pedido {}: estoque reservado ({} itens)", pedidoId, itens.size());
		publicarResultado(pedidoId, pedido, reserva);
	}

	/**
	 * O mesmo pedido.criado chegou de novo. Pode ser que o resultado tenha sido gravado, mas a
	 * publicação falhou (o Kafka entrega de novo justamente porque deu erro): republica o
	 * resultado guardado. Quem escuta aguenta o repetido (filtro de estado no pedidos).
	 */
	private void republicar(String pedidoId, PedidoCriado pedido) {
		Reserva existente = reservas.findByPedidoId(pedidoId).orElse(null);
		if (existente == null || existente.getStatus() == Reserva.Status.PROCESSANDO) {
			// Caiu no meio da reserva da outra vez: não dá para saber o que já saiu do estoque.
			// Limitação conhecida (ver README): resolver exige transação ou Outbox.
			log.warn("Pedido {} repetido com a reserva pela metade: ignorado", pedidoId);
			return;
		}
		log.info("Pedido {} repetido: já estava {}, republica o resultado", pedidoId, existente.getStatus());
		publicarResultado(pedidoId, pedido, existente);
	}

	private void publicarResultado(String pedidoId, PedidoCriado pedido, Reserva reserva) {
		if (reserva.getStatus() == Reserva.Status.RESERVADO) {
			eventos.publicar(KafkaConfig.TOPICO_ESTOQUE, EstoqueEventos.RESERVADO, pedidoId,
					EstoqueEventos.Reservado.de(pedido));
		} else {
			eventos.publicar(KafkaConfig.TOPICO_ESTOQUE, EstoqueEventos.INSUFICIENTE, pedidoId,
					EstoqueEventos.Insuficiente.de(pedido, reserva.getMotivo()));
		}
	}

	// O texto que vai para o pedido (motivoCancelamento) e para o e-mail do cliente
	private String motivo(Reserva.Item item) {
		Produto produto = produtos.findById(item.produtoId()).orElse(null);
		if (produto == null || !produto.isAtivo()) {
			return "Produto indisponível: " + (produto == null ? item.produtoId() : produto.getNome()) + ".";
		}
		return "Estoque insuficiente para " + produto.getNome() + ": pedido " + item.quantidade() + ", disponível "
				+ produto.getEstoque().disponivel() + ".";
	}

}
