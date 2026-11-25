package br.com.ecommerce.pagamentos.banco;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;

/**
 * O disjuntor da chamada ao banco (Resilience4j, o equivalente Java do opossum dos slides).
 *
 *   FECHADO     as chamadas passam; conta as falhas numa janela das últimas 10 chamadas
 *   ABERTO      50% de falha em pelo menos 5 chamadas: falha na hora, sem chamar o banco
 *   MEIO-ABERTO passados 15 s, deixa UMA chamada testar: deu certo, fecha; falhou, abre de novo
 *
 * Falha, para o circuito, é só BancoIndisponivelException (timeout, sem conexão, 5xx). Um 402 é o
 * banco respondendo "não": sucesso para o circuito.
 *
 * O estado fica na memória de cada réplica: com 3 réplicas, cada uma tem o seu circuito.
 */
@Configuration
public class BancoConfig {

	private static final Logger log = LoggerFactory.getLogger(BancoConfig.class);

	@Bean
	CircuitBreaker circuitoBanco(@Value("${banco.circuito.espera-aberto}") Duration esperaAberto) {
		CircuitBreaker circuito = CircuitBreaker.of("banco", CircuitBreakerConfig.custom()
				.slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
				.slidingWindowSize(10)
				// volumeThreshold do opossum: sem ele, a 1ª falha já seria 100% e abriria o circuito
				.minimumNumberOfCalls(5)
				.failureRateThreshold(50)
				.waitDurationInOpenState(esperaAberto)
				.permittedNumberOfCallsInHalfOpenState(1)
				// Passa para MEIO-ABERTO sozinho (o /health mostra), sem esperar a próxima chamada
				.automaticTransitionFromOpenToHalfOpenEnabled(true)
				.recordExceptions(BancoIndisponivelException.class)
				.build());
		circuito.getEventPublisher().onStateTransition(evento -> {
			String para = estado(evento.getStateTransition().getToState());
			if ("aberto".equals(para)) {
				log.warn("CIRCUITO ABERTO: o banco falhou demais, chamadas barradas por {} s", esperaAberto.toSeconds());
			} else if ("meio-aberto".equals(para)) {
				log.warn("CIRCUITO MEIO-ABERTO: a próxima cobrança testa se o banco voltou");
			} else {
				log.warn("CIRCUITO FECHADO: o banco voltou");
			}
		});
		return circuito;
	}

	// Em português, para o log e o /health
	public static String estado(CircuitBreaker.State estado) {
		return switch (estado) {
			case CLOSED -> "fechado";
			case OPEN, FORCED_OPEN -> "aberto";
			case HALF_OPEN -> "meio-aberto";
			default -> estado.name().toLowerCase();
		};
	}

}
