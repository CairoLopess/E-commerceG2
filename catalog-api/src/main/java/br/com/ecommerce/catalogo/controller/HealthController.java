package br.com.ecommerce.catalogo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.catalogo.cache.VitrineCache;
import br.com.ecommerce.catalogo.evento.KafkaSaude;

/**
 * { servico, status, dependencias: { banco, kafka, cache } }.
 *
 *   sem o Mongo ou sem o Kafka -> "fora do ar", 503
 *   sem o Redis                -> "degradado", 200: o cache só acelera, a vitrine continua (OFF)
 */
@RestController
public class HealthController {

	private final MongoTemplate mongoTemplate;
	private final KafkaSaude kafka;
	private final VitrineCache cache;

	public HealthController(MongoTemplate mongoTemplate, KafkaSaude kafka, VitrineCache cache) {
		this.mongoTemplate = mongoTemplate;
		this.kafka = kafka;
		this.cache = cache;
	}

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public ResponseEntity<Saude> health() {
		Map<String, String> dependencias = new LinkedHashMap<>();
		dependencias.put("banco", bancoNoAr() ? "ok" : "fora");
		dependencias.put("kafka", kafka.noAr() ? "ok" : "fora");
		boolean fora = dependencias.containsValue("fora");
		dependencias.put("cache", cache.noAr() ? "ok" : "fora");
		String status = fora ? "fora do ar" : dependencias.containsValue("fora") ? "degradado" : "ok";
		return ResponseEntity.status(fora ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
				.body(new Saude("catalogo", status, dependencias));
	}

	private boolean bancoNoAr() {
		try {
			mongoTemplate.executeCommand("{ ping: 1 }");
			return true;
		} catch (Exception ex) {
			return false;
		}
	}

}
