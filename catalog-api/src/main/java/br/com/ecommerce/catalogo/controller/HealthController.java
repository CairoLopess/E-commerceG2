package br.com.ecommerce.catalogo.controller;

import java.util.Map;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final MongoTemplate mongoTemplate;

	public HealthController(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		try {
			mongoTemplate.executeCommand("{ ping: 1 }");
			return ResponseEntity.ok(Map.of("status", "ok", "banco", "ok"));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("status", "erro", "banco", "indisponível"));
		}
	}

}
