package br.com.ecommerce.notificacoes;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.notificacoes.evento.KafkaSaude;

/**
 * { servico, status, dependencias: { kafka, smtp } }. Sem banco: o "banco" do notificacoes é o
 * SMTP. Sem o Kafka não chega evento; sem o SMTP não sai e-mail. Os dois são essenciais: 503.
 * O SMTP é testado só abrindo a conexão (sem mandar nada), para o /health continuar barato.
 */
@RestController
public class HealthController {

	private final KafkaSaude kafka;
	private final String smtpHost;
	private final int smtpPorta;

	public HealthController(KafkaSaude kafka, @Value("${spring.mail.host}") String smtpHost,
			@Value("${spring.mail.port}") int smtpPorta) {
		this.kafka = kafka;
		this.smtpHost = smtpHost;
		this.smtpPorta = smtpPorta;
	}

	public record Saude(String servico, String status, Map<String, String> dependencias) {
	}

	@GetMapping("/health")
	public ResponseEntity<Saude> health() {
		Map<String, String> dependencias = new LinkedHashMap<>();
		dependencias.put("kafka", kafka.noAr() ? "ok" : "fora");
		dependencias.put("smtp", smtpNoAr() ? "ok" : "fora");
		boolean fora = dependencias.containsValue("fora");
		return ResponseEntity.status(fora ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK)
				.body(new Saude("notificacoes", fora ? "fora do ar" : "ok", dependencias));
	}

	private boolean smtpNoAr() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(smtpHost, smtpPorta), 1000);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

}
