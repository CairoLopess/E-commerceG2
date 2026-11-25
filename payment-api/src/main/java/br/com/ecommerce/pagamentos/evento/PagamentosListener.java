package br.com.ecommerce.pagamentos.evento;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ecommerce.pagamentos.evento.PagamentoEventos.EstoqueReservado;
import br.com.ecommerce.pagamentos.evento.PagamentoEventos.PedidoCancelado;
import br.com.ecommerce.pagamentos.service.PagamentoService;

/**
 * Escuta o tópico estoque (não o pedidos!) para cobrar: só cobra o que já tem estoque reservado.
 * E escuta o tópico pedidos só pelo pedido.cancelado, para estornar ou bloquear a cobrança.
 *
 * Um listener só para os dois tópicos: as mensagens são tratadas uma de cada vez, numa thread.
 * Como a chave é o id do pedido e os dois tópicos têm 3 partições, os eventos de um pedido caem
 * no mesmo número de partição nos dois, e o Kafka entrega as duas à mesma réplica (RangeAssignor).
 * Assim, a cobrança e o cancelamento do mesmo pedido não rodam ao mesmo tempo.
 */
@Component
public class PagamentosListener {

	private final Eventos eventos;
	private final Map<String, Consumer<Evento>> handlers;

	public PagamentosListener(Eventos eventos, PagamentoService pagamentos) {
		this.eventos = eventos;
		this.handlers = Map.of(
				"estoque.reservado", e -> pagamentos.cobrar(e.pedidoId(), eventos.dados(e, EstoqueReservado.class)),
				"pedido.cancelado", e -> pagamentos.aoCancelar(e.pedidoId(), eventos.dados(e, PedidoCancelado.class)));
	}

	@KafkaListener(topics = { KafkaConfig.TOPICO_ESTOQUE, KafkaConfig.TOPICO_PEDIDOS }, groupId = "pagamentos")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

}
