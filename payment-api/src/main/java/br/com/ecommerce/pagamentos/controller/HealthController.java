package br.com.ecommerce.pagamentos.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pagamentos.banco.BancoConfig;
import br.com.ecommerce.pagamentos.banco.CobrancaResiliente;

/**
 * { status, banco, circuitoBancoExterno: fechado | aberto | meio-aberto }.
 * Circuito aberto não é o pagamentos fora do ar (continua 200): é o banco do cartão com problema,
 * e quem olha o painel sabe na hora o que está acontecendo.
 */
@RestController
public class HealthController {

	private final JdbcTemplate jdbcTemplate;
	private final CobrancaResiliente cobranca;

	public HealthController(JdbcTemplate jdbcTemplate, CobrancaResiliente cobranca) {
		this.jdbcTemplate = jdbcTemplate;
		this.cobranca = cobranca;
	}

	@GetMapping("/health")
	public ResponseEntity<Map<String, String>> health() {
		String circuito = BancoConfig.estado(cobranca.estadoCircuito());
		try {
			jdbcTemplate.queryForObject("SELECT 1", Integer.class);
			return ResponseEntity.ok(Map.of("status", "ok", "banco", "ok", "circuitoBancoExterno", circuito));
		} catch (Exception ex) {
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Map.of("status", "erro", "banco", "indisponível", "circuitoBancoExterno", circuito));
		}
	}

}
