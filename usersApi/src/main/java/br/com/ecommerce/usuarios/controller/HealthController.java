package br.com.ecommerce.usuarios.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * { servico, status, dependencias }. O usuarios não usa Kafka: a única dependência é o próprio
 * banco. Sem ele, "fora do ar" e 503.
 */
@RestController
public class HealthController {

	private final JdbcTemplate jdbcTemplate;

	public HealthController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public ResponseEntity<Saude> health() {
		Map<String, String> dependencias = new LinkedHashMap<>();
		dependencias.put("banco", bancoNoAr() ? "ok" : "fora");
		boolean fora = dependencias.containsValue("fora");
		return ResponseEntity.status(fora ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
				.body(new Saude("usuarios", fora ? "fora do ar" : "ok", dependencias));
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
