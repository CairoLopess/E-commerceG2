package br.com.ecommerce.banco;

import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * O banco do cartão, que não é nosso: o sistema externo da saga. Fica fora do Kong, na porta 9000,
 * e se comporta como mandarem em POST /config: demora (latenciaMs) e falha (falhaPercent, 503).
 *
 * Regras:
 *   cartão final 0002              -> 402 { motivo: "saldo insuficiente" }
 *   outro cartão, pix ou boleto    -> 201 { aprovado: true, codigo: "TX-..." }
 *   mesma referência outra vez     -> a mesma resposta, sem cobrar de novo (chave de idempotência)
 *
 * A cobrança é registrada ANTES de esperar a latência: com latenciaMs acima do timeout de quem
 * chama, o banco cobra e a resposta não chega a tempo ("timeout não é 'não aconteceu'").
 * Sem banco de dados: reiniciou, esqueceu tudo.
 */
@RestController
public class BancoController {

	static final String CARTAO_SEM_SALDO = "0002";

	private static final Logger log = LoggerFactory.getLogger(BancoController.class);

	// referencia -> cobrança. A referência é o id do pedido.
	private final Map<String, Cobranca> cobrancas = new ConcurrentHashMap<>();

	private volatile Config config;

	public BancoController(@Value("${banco.latencia-ms}") int latenciaMs,
			@Value("${banco.falha-percent}") int falhaPercent) {
		this.config = new Config(latenciaMs, falhaPercent);
	}

	public record CobrancaRequest(BigDecimal valor, String cartaoFinal, String referencia, String forma) {
	}

	public record EstornoRequest(String codigo, String referencia) {
	}

	public record Config(Integer latenciaMs, Integer falhaPercent) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Resposta(Boolean aprovado, String codigo, String referencia, BigDecimal valor, String motivo,
			String erro) {

		static Resposta erro(String erro) {
			return new Resposta(null, null, null, null, null, erro);
		}

	}

	// O que o banco guarda de cada referência: a resposta dada (para repetir igual) e se já foi estornada
	private record Cobranca(HttpStatus status, Resposta resposta, boolean estornada) {
	}

	@PostMapping("/cobrancas")
	public ResponseEntity<Resposta> cobrar(@RequestBody CobrancaRequest req) {
		if (req.referencia() == null || req.referencia().isBlank() || req.valor() == null
				|| req.valor().signum() <= 0) {
			return ResponseEntity.badRequest().body(Resposta.erro("referencia e valor (> 0) são obrigatórios"));
		}
		Config agora = config;

		// Falha simulada: o banco "caiu" antes de processar, nada foi cobrado
		if (ThreadLocalRandom.current().nextInt(100) < agora.falhaPercent()) {
			esperar(agora.latenciaMs());
			log.warn("Cobrança {}: 503 (falha simulada, falhaPercent {})", req.referencia(), agora.falhaPercent());
			return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
					.body(Resposta.erro("banco indisponível (falha simulada)"));
		}

		// Idempotência: já vi essa referência? Devolve a mesma resposta e não cobra de novo
		Cobranca anterior = cobrancas.get(req.referencia());
		Cobranca cobranca = cobrancas.computeIfAbsent(req.referencia(), ref -> novaCobranca(req));
		if (anterior != null) {
			log.info("Cobrança {} REPETIDA: devolvendo a mesma ({}), sem cobrar de novo", req.referencia(),
					cobranca.resposta().codigo() != null ? cobranca.resposta().codigo() : cobranca.status().value());
		}

		esperar(agora.latenciaMs());
		return ResponseEntity.status(cobranca.status()).body(cobranca.resposta());
	}

	private Cobranca novaCobranca(CobrancaRequest req) {
		if (CARTAO_SEM_SALDO.equals(req.cartaoFinal())) {
			log.info("Cobrança {} de R$ {}: RECUSADA (saldo insuficiente)", req.referencia(), req.valor());
			return new Cobranca(HttpStatus.PAYMENT_REQUIRED,
					new Resposta(false, null, req.referencia(), req.valor(), "saldo insuficiente", null), false);
		}
		String codigo = "TX-" + HexFormat.of().withUpperCase().toHexDigits(ThreadLocalRandom.current().nextInt());
		log.info("Cobrança {} de R$ {}: aprovada ({})", req.referencia(), req.valor(), codigo);
		return new Cobranca(HttpStatus.CREATED,
				new Resposta(true, codigo, req.referencia(), req.valor(), null, null), false);
	}

	/**
	 * Estorno pela referência (e/ou pelo código). Sem cobrança aprovada com essa referência: 404,
	 * e tudo bem (é o estorno "por segurança" depois de um timeout). Estornar de novo: 201, igual.
	 * O falhaPercent não vale aqui: devolver dinheiro é importante demais para a simulação barrar.
	 */
	@PostMapping("/estornos")
	public ResponseEntity<Resposta> estornar(@RequestBody EstornoRequest req) {
		Config agora = config;
		Map.Entry<String, Cobranca> achada = cobrancas.entrySet().stream()
				.filter(e -> e.getKey().equals(req.referencia())
						|| (req.codigo() != null && req.codigo().equals(e.getValue().resposta().codigo())))
				.filter(e -> e.getValue().status() == HttpStatus.CREATED)
				.findFirst()
				.orElse(null);
		esperar(agora.latenciaMs());
		if (achada == null) {
			log.info("Estorno de {} / {}: nenhuma cobrança aprovada, nada a estornar (404)", req.codigo(),
					req.referencia());
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Resposta.erro("cobrança não encontrada"));
		}
		Cobranca cobranca = achada.getValue();
		if (!cobranca.estornada()) {
			cobrancas.put(achada.getKey(), new Cobranca(cobranca.status(), cobranca.resposta(), true));
			log.info("ESTORNO da cobrança {} (pedido {}, R$ {})", cobranca.resposta().codigo(), achada.getKey(),
					cobranca.resposta().valor());
		} else {
			log.info("Estorno da cobrança {} REPETIDO: já estava estornada", cobranca.resposta().codigo());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(new Resposta(null, cobranca.resposta().codigo(),
				achada.getKey(), cobranca.resposta().valor(), null, null));
	}

	@GetMapping("/config")
	public Config verConfig() {
		return config;
	}

	// Muda só o que vier: { "falhaPercent": 30 } mantém a latência
	@PostMapping("/config")
	public ResponseEntity<?> mudarConfig(@RequestBody Config nova) {
		int latencia = nova.latenciaMs() != null ? nova.latenciaMs() : config.latenciaMs();
		int falha = nova.falhaPercent() != null ? nova.falhaPercent() : config.falhaPercent();
		if (latencia < 0 || latencia > 60_000 || falha < 0 || falha > 100) {
			return ResponseEntity.badRequest().body(Resposta.erro("latenciaMs de 0 a 60000, falhaPercent de 0 a 100"));
		}
		config = new Config(latencia, falha);
		log.warn("Config: latenciaMs={} falhaPercent={}", latencia, falha);
		return ResponseEntity.ok(config);
	}

	private static void esperar(int ms) {
		if (ms <= 0) {
			return;
		}
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
