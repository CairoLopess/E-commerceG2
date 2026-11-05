package br.com.ecommerce.notificacoes.evento;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import br.com.ecommerce.notificacoes.email.EmailService;

/**
 * Escuta os dois tópicos e avisa o cliente. O e-mail vem dentro do evento (clienteEmail): o
 * serviço não precisa consultar o usuarios nem o pedidos.
 *
 * Pelo menos uma vez: se o Kafka entregar o mesmo evento de novo, sai um e-mail repetido.
 * Para um aviso, é aceitável (ver README).
 */
@Component
public class NotificacoesListener {

	private final Eventos eventos;
	private final EmailService email;
	private final Map<String, Consumer<Evento>> handlers;

	public NotificacoesListener(Eventos eventos, EmailService email) {
		this.eventos = eventos;
		this.email = email;
		this.handlers = Map.of(
				"pedido.criado", this::pedidoRecebido,
				"estoque.insuficiente", this::semEstoque);
	}

	@KafkaListener(topics = { KafkaConfig.TOPICO_PEDIDOS, KafkaConfig.TOPICO_ESTOQUE }, groupId = "notificacoes")
	public void receber(String mensagem) {
		eventos.tratar(mensagem, handlers);
	}

	private void pedidoRecebido(Evento evento) {
		PedidoCriado pedido = eventos.dados(evento, PedidoCriado.class);
		String itens = pedido.itens().stream()
				.map(item -> "- " + item.quantidade() + "x " + item.nome() + " (" + reais(item.precoUnitario()) + ")")
				.collect(Collectors.joining("\n"));
		email.enviar(pedido.clienteEmail(), "Recebemos o seu pedido", """
				Olá!

				Recebemos o seu pedido %s.

				%s

				Total: %s
				Pagamento: %s

				Agora estamos separando os produtos no estoque. Se faltar algum, avisamos por aqui.
				""".formatted(evento.pedidoId(), itens, reais(pedido.valorTotal()), pedido.formaPagamento()));
	}

	private void semEstoque(Evento evento) {
		EstoqueInsuficiente dados = eventos.dados(evento, EstoqueInsuficiente.class);
		email.enviar(dados.clienteEmail(), "Seu pedido foi cancelado: sem estoque", """
				Olá!

				Infelizmente o seu pedido %s foi cancelado.

				%s

				Nada foi cobrado. Os produtos continuam no seu histórico de pedidos.
				""".formatted(evento.pedidoId(), dados.motivo()));
	}

	private static String reais(BigDecimal valor) {
		// O formato pt-BR põe um espaço não separável depois do "R$": vira espaço comum no texto
		return valor == null ? "-"
				: NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor).replace(' ', ' ');
	}

	// Só o que as notificações usam de cada evento; o resto é ignorado

	@JsonIgnoreProperties(ignoreUnknown = true)
	record PedidoCriado(String clienteEmail, BigDecimal valorTotal, String formaPagamento, List<Item> itens) {

		@JsonIgnoreProperties(ignoreUnknown = true)
		record Item(String nome, BigDecimal precoUnitario, int quantidade) {
		}

		PedidoCriado {
			itens = itens == null ? List.of() : itens;
		}

	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record EstoqueInsuficiente(String motivo, String clienteEmail) {
	}

}
