package br.com.ecommerce.catalogo.evento;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ecommerce.catalogo.service.ReservaService;

/**
 * O catálogo escuta o tópico pedidos. Só pedido.criado interessa: o resto é ignorado.
 * groupId = nome do serviço: com 3 réplicas do catálogo, cada uma fica com uma partição.
 */
@Component
public class PedidosListener {

	private final Eventos eventos;
	private final Map<String, Consumer<Evento>> handlers;

	public PedidosListener(Eventos eventos, ReservaService reservas) {
		this.eventos = eventos;
		this.handlers = Map.of(
				"pedido.criado", e -> reservas.reservar(e.pedidoId(), eventos.dados(e, PedidoCriado.class)));
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_PEDIDOS, groupId = "catalogo")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

}
