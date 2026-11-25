package br.com.ecommerce.catalogo.evento;

import java.util.Map;
import java.util.function.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ecommerce.catalogo.service.ReservaService;

/**
 * O fim da saga para o estoque (tópico pagamentos):
 *   pagamento.aprovado -> confirma a reserva (sai do reservado)
 *   pagamento.recusado -> COMPENSAÇÃO: devolve o estoque ao disponível
 *
 * O catálogo não sabe por que o pagamento foi recusado (cartão, banco fora): só desfaz a própria parte.
 */
@Component
public class PagamentosListener {

	private final Eventos eventos;
	private final Map<String, Consumer<Evento>> handlers;

	public PagamentosListener(Eventos eventos, ReservaService reservas) {
		this.eventos = eventos;
		this.handlers = Map.of(
				"pagamento.aprovado", e -> reservas.confirmar(e.pedidoId()),
				"pagamento.recusado", e -> reservas.liberar(e.pedidoId(), "pagamento recusado"));
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_PAGAMENTOS, groupId = "catalogo")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

}
