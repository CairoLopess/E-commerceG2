package br.com.ecommerce.catalogo.evento;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publicar e ler eventos, sempre no mesmo envelope. A mesma classe existe em cada serviço
 * (pedidos, catalogo, notificacoes): quem publica e quem lê combinam só o formato.
 *
 * O correlationId vem do contexto de log (MDC): numa requisição, é o X-Request-Id do Kong
 * (ver config/RequestIdFilter); ao tratar um evento, é o correlationId do evento recebido.
 * Assim ele segue de evento em evento sem ninguém passar de mão em mão.
 */
@Component
public class Eventos {

	// A mesma chave de MDC do RequestIdFilter: as linhas de log saem com o id
	public static final String MDC_KEY = "requestId";

	// O mesmo filtro do RequestIdFilter: um id forjado não entra no log
	private static final Pattern ID_VALIDO = Pattern.compile("[A-Za-z0-9._-]{1,64}");

	private static final long TIMEOUT_ENVIO_S = 10;

	private static final Logger log = LoggerFactory.getLogger(Eventos.class);

	private final KafkaTemplate<String, String> kafka;
	private final JsonMapper json;

	public Eventos(KafkaTemplate<String, String> kafka, JsonMapper json) {
		this.kafka = kafka;
		// Valores com casas decimais viram BigDecimal: 2239.70 não pode virar 2239.6999...
		this.json = json.rebuild().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
	}

	/**
	 * Publica e espera o Kafka confirmar. A chave da mensagem é o id do pedido: todos os eventos de
	 * um pedido caem na mesma partição e chegam em ordem. Falhou (Kafka fora): lança exceção.
	 */
	public Evento publicar(String topico, String tipo, String pedidoId, Object dados) {
		Evento evento = new Evento(UUID.randomUUID().toString(), tipo, pedidoId,
				Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(), MDC.get(MDC_KEY), json.valueToTree(dados));
		try {
			// value é texto: o Kafka não sabe o que é JSON. Quem lê faz o parse.
			kafka.send(topico, pedidoId, json.writeValueAsString(evento)).get(TIMEOUT_ENVIO_S, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrompido ao publicar " + tipo + ".", e);
		} catch (Exception e) {
			throw new IllegalStateException("Falha ao publicar " + tipo + " do pedido " + pedidoId + ".", e);
		}
		log.info("Publicado {} do pedido {} no tópico {}", tipo, pedidoId, topico);
		return evento;
	}

	/**
	 * Lê a mensagem e chama o handler do tipo. Tipo que não interessa a este serviço: ignora.
	 * Durante o handler, o correlationId do evento vai para o log (e para os eventos publicados).
	 * Se o handler lançar exceção, o offset não é confirmado e o Kafka entrega de novo (pelo
	 * menos uma vez): por isso todo handler precisa aguentar receber o mesmo evento duas vezes.
	 */
	public void tratar(String mensagem, Map<String, Consumer<Evento>> handlers) {
		Evento evento = json.readValue(mensagem, Evento.class);
		Consumer<Evento> handler = handlers.get(evento.tipo());
		if (handler == null) {
			return;
		}
		if (evento.correlationId() != null && ID_VALIDO.matcher(evento.correlationId()).matches()) {
			MDC.put(MDC_KEY, evento.correlationId());
		}
		try {
			log.info("Recebido {} do pedido {}", evento.tipo(), evento.pedidoId());
			handler.accept(evento);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}

	// O conteúdo de "dados" no formato que o handler espera
	public <T> T dados(Evento evento, Class<T> tipo) {
		return json.treeToValue(evento.dados(), tipo);
	}

}
