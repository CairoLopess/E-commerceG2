package br.com.ecommerce.pagamentos.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pagamentos.banco.BancoConfig;
import br.com.ecommerce.pagamentos.banco.CobrancaResiliente;
import br.com.ecommerce.pagamentos.evento.KafkaSaude;

/**
 * { servico, status, dependencias: { banco, kafka, circuitoBancoExterno } }.
 *
 *   sem o banco ou sem o Kafka       -> "fora do ar", 503 (não consegue trabalhar)
 *   circuito aberto ou meio-aberto   -> "degradado", 200 (faz o que deve: recusar rápido)
 *
 * Um 503 com o banco do cartão fora tiraria o pagamentos do ar à toa: reiniciá-lo não traria o
 * banco do cartão de volta.
 */
@RestController
public class HealthController {

	private final JdbcTemplate jdbcTemplate;
	private final KafkaSaude kafka;
	private final CobrancaResiliente cobranca;

	public HealthController(JdbcTemplate jdbcTemplate, KafkaSaude kafka, CobrancaResiliente cobranca) {
		this.jdbcTemplate = jdbcTemplate;
		this.kafka = kafka;
		this.cobranca = cobranca;
	}

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public ResponseEntity<Saude> health() {
		Map<String, String> dependencias = new LinkedHashMap<>();
		dependencias.put("banco", bancoNoAr() ? "ok" : "fora");
		dependencias.put("kafka", kafka.noAr() ? "ok" : "fora");
		boolean fora = dependencias.containsValue("fora");
		String circuito = BancoConfig.estado(cobranca.estadoCircuito());
		dependencias.put("circuitoBancoExterno", circuito);
		String status = fora ? "fora do ar" : "fechado".equals(circuito) ? "ok" : "degradado";
		return ResponseEntity.status(fora ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
				.body(new Saude("pagamentos", status, dependencias));
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
