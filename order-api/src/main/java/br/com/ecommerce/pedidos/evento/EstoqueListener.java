package br.com.ecommerce.pedidos.evento;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ecommerce.pedidos.entity.StatusPedido;
import br.com.ecommerce.pedidos.service.PedidoService;

/**
 * Reações aos eventos do catálogo (tópico estoque). Coreografia: o pedidos não sabe que o
 * catálogo reserva; só reage ao resultado.
 *
 * Filtro de estado: o status só muda a partir de AGUARDANDO_ESTOQUE. Um estoque.reservado
 * repetido (ou atrasado, depois de o cliente cancelar) não leva o pedido para trás.
 */
@Component
public class EstoqueListener {

	private static final Set<StatusPedido> SO_DE_AGUARDANDO_ESTOQUE = Set.of(StatusPedido.AGUARDANDO_ESTOQUE);

	private final Eventos eventos;
	private final Map<String, Consumer<Evento>> handlers;

	public EstoqueListener(Eventos eventos, PedidoService pedidos) {
		this.eventos = eventos;
		this.handlers = Map.of(
				"estoque.reservado", e -> pedidos.mudarStatus(e.pedidoId(), StatusPedido.AGUARDANDO_PAGAMENTO,
						SO_DE_AGUARDANDO_ESTOQUE, null),
				"estoque.insuficiente", e -> pedidos.mudarStatus(e.pedidoId(), StatusPedido.CANCELADO,
						SO_DE_AGUARDANDO_ESTOQUE, e.dados().path("motivo").asString(null)));
	}

	// groupId = nome do serviço: réplicas do pedidos dividem as partições entre si
	@KafkaListener(topics = KafkaConfig.TOPICO_ESTOQUE, groupId = "pedidos")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

}
