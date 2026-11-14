package br.com.ecommerce.pedidos.evento;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import br.com.ecommerce.pedidos.entity.StatusPedido;
import br.com.ecommerce.pedidos.service.PedidoService;

/**
 * Reações ao resultado do pagamento (tópico pagamentos).
 *
 *   pagamento.aprovado -> PAGO
 *   pagamento.recusado -> CANCELADO, com o motivo (saldo insuficiente, banco indisponível...)
 *
 * Os dois aceitam AGUARDANDO_ESTOQUE como origem, além de AGUARDANDO_PAGAMENTO: o pagamentos
 * reage ao mesmo estoque.reservado que o pedidos, e pode terminar antes de o pedidos tratar o seu
 * (tópicos diferentes não têm ordem entre si). Um pagamento.aprovado só existe se o estoque foi
 * reservado, então pular de AGUARDANDO_ESTOQUE para PAGO é seguro; o estoque.reservado que chegar
 * depois é ignorado pelo filtro de estado.
 *
 * O filtro de estado é o cadeado: um pedido CANCELADO não volta a PAGO porque o banco aprovou
 * atrasado (o pagamentos estorna essa cobrança ao receber o pedido.cancelado).
 */
@Component
public class PagamentosListener {

	private static final Set<StatusPedido> AGUARDANDO = Set.of(StatusPedido.AGUARDANDO_ESTOQUE,
			StatusPedido.AGUARDANDO_PAGAMENTO);

	private final Eventos eventos;
	private final Map<String, Consumer<Evento>> handlers;

	public PagamentosListener(Eventos eventos, PedidoService pedidos) {
		this.eventos = eventos;
		this.handlers = Map.of(
				"pagamento.aprovado", e -> pedidos.mudarStatus(e.pedidoId(), StatusPedido.PAGO, AGUARDANDO, null),
				"pagamento.recusado", e -> pedidos.mudarStatus(e.pedidoId(), StatusPedido.CANCELADO, AGUARDANDO,
						"Pagamento recusado: " + e.dados().path("motivo").asString("motivo não informado") + "."));
	}

	@KafkaListener(topics = KafkaConfig.TOPICO_PAGAMENTOS, groupId = "pedidos")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

}
