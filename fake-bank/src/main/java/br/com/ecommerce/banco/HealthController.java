package br.com.ecommerce.banco;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só para o healthcheck do Docker: o banco-falso não tem dependências. A falha simulada
 * (falhaPercent) vale para as cobranças, não para o /health: quem quer saber se o banco do cartão
 * está falhando olha o circuito no /health do pagamentos.
 */
@RestController
public class HealthController {

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public Saude health() {
		return new Saude("banco-falso", "ok", Map.of());
	}

}
