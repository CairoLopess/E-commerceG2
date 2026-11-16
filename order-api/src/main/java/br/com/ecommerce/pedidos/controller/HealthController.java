package br.com.ecommerce.pedidos.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pedidos.evento.KafkaSaude;

/**
 * { servico, status, dependencias }. Sem o banco ou sem o Kafka o pedidos não consegue trabalhar:
 * "fora do ar", 503. Não chama o /health do catálogo: o health diz se ESTE serviço consegue
 * trabalhar; a falta do catálogo aparece no 503 do checkout e no health do próprio catálogo.
 */
@RestController
public class HealthController {

	private final JdbcTemplate jdbcTemplate;
	private final KafkaSaude kafka;

	public HealthController(JdbcTemplate jdbcTemplate, KafkaSaude kafka) {
		this.jdbcTemplate = jdbcTemplate;
		this.kafka = kafka;
	}

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public ResponseEntity<Saude> health() {
		Map<String, String> dependencias = new LinkedHashMap<>();
		dependencias.put("banco", bancoNoAr() ? "ok" : "fora");
		dependencias.put("kafka", kafka.noAr() ? "ok" : "fora");
		boolean fora = dependencias.containsValue("fora");
		return ResponseEntity.status(fora ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
				.body(new Saude("pedidos", fora ? "fora do ar" : "ok", dependencias));
	}

	private boolean bancoNoAr() {
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

}
