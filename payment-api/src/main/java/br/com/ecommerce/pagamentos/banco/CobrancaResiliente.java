package br.com.ecommerce.pagamentos.banco;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import br.com.ecommerce.pagamentos.entity.StatusPagamento;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

/**
 * A chamada ao banco com as três defesas, uma por cima da outra:
 *
 *   timeout          cada chamada desiste em 2 s (no BancoClient)
 *   retry            até 3 tentativas, esperando 500 ms e 1 s (mais jitter), só em falha passageira
 *   circuit breaker  em volta de cada tentativa: aberto, nem tenta (falha na hora)
 *
 * O retry é seguro porque a referência (id do pedido) é a chave de idempotência do banco: a 2ª
 * tentativa de uma cobrança que o banco já fez devolve a mesma, sem cobrar de novo.
 *
 * O retry fica só aqui, na camada mais próxima de quem falha (pagamentos -> banco). Ninguém está
 * esperando do outro lado (é por evento), então dá para insistir um pouco.
 */
@Component
public class CobrancaResiliente {

	public static final int TENTATIVAS = 3;

	private static final Logger log = LoggerFactory.getLogger(CobrancaResiliente.class);

	private final BancoClient banco;
	private final CircuitBreaker circuito;
	private final Duration espera;

	public CobrancaResiliente(BancoClient banco, CircuitBreaker circuitoBanco,
			@Value("${banco.retry.espera}") Duration espera) {
		this.banco = banco;
		this.circuito = circuitoBanco;
		this.espera = espera;
	}

	public ResultadoCobranca cobrar(UUID pedidoId, BigDecimal valor, String forma, String cartaoFinal) {
		boolean houveTimeout = false;
		String ultimaFalha = null;
		int tentativas = 0;
		for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
			// Circuito aberto: não adianta insistir, nem a primeira
			if (!circuito.tryAcquirePermission()) {
				log.warn("Pedido {}: circuito aberto, cobrança não tentada", pedidoId);
				ultimaFalha = "circuito aberto";
				break;
			}
			tentativas++;
			long inicio = System.nanoTime();
			try {
				String codigo = banco.cobrar(pedidoId, valor, forma, cartaoFinal);
				circuito.onSuccess(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
				log.info("Pedido {}: tentativa {}/{} aprovada ({})", pedidoId, tentativa, TENTATIVAS, codigo);
				return new ResultadoCobranca(StatusPagamento.APROVADO, codigo, null, tentativas, houveTimeout);
			} catch (BancoRecusouException recusa) {
				// Uma resposta: o banco está de pé (sucesso para o circuito) e repetir não muda o "não"
				circuito.onSuccess(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
				log.info("Pedido {}: tentativa {}/{} recusada pelo banco: {}", pedidoId, tentativa, TENTATIVAS,
						recusa.getMessage());
				return new ResultadoCobranca(StatusPagamento.RECUSADO, null, recusa.getMessage(), tentativas,
						houveTimeout);
			} catch (BancoIndisponivelException falha) {
				circuito.onError(System.nanoTime() - inicio, TimeUnit.NANOSECONDS, falha);
				houveTimeout |= falha.isTimeout();
				ultimaFalha = falha.getMessage();
				log.warn("Pedido {}: tentativa {}/{} falhou: {}", pedidoId, tentativa, TENTATIVAS, ultimaFalha);
				if (tentativa < TENTATIVAS) {
					esperar(tentativa);
				}
			} catch (RuntimeException inesperado) {
				// Qualquer outra coisa (resposta ilegível...): falha, sem deixar a permissão presa
				circuito.onError(System.nanoTime() - inicio, TimeUnit.NANOSECONDS, inesperado);
				ultimaFalha = "erro inesperado";
				log.error("Pedido {}: tentativa {}/{} com erro inesperado", pedidoId, tentativa, TENTATIVAS, inesperado);
				break;
			}
		}
		return new ResultadoCobranca(StatusPagamento.ERRO, null, "banco indisponível (" + ultimaFalha + ")", tentativas,
				houveTimeout);
	}

	/**
	 * Estorno: sem circuit breaker (devolver dinheiro é importante demais para ser barrado), com
	 * retry e um timeout maior. É idempotente: o banco estorna cada cobrança uma vez só.
	 * true = estornado; false = não havia cobrança aprovada (404). Esgotou: lança a última falha.
	 */
	public boolean estornar(String codigo, UUID pedidoId) {
		BancoIndisponivelException ultima = null;
		for (int tentativa = 1; tentativa <= TENTATIVAS; tentativa++) {
			try {
				boolean estornado = banco.estornar(codigo, pedidoId);
				log.info("Pedido {}: estorno {} na tentativa {}/{}", pedidoId,
						estornado ? "feito" : "sem cobrança a devolver (404)", tentativa, TENTATIVAS);
				return estornado;
			} catch (BancoIndisponivelException falha) {
				ultima = falha;
				log.warn("Pedido {}: estorno, tentativa {}/{} falhou: {}", pedidoId, tentativa, TENTATIVAS,
						falha.getMessage());
				if (tentativa < TENTATIVAS) {
					esperar(tentativa);
				}
			}
		}
		throw ultima;
	}

	public CircuitBreaker.State estadoCircuito() {
		return circuito.getState();
	}

	// Backoff exponencial (500 ms, 1 s) com jitter: mil clientes não tentam no mesmo milissegundo
	private void esperar(int tentativa) {
		long base = espera.toMillis() * (1L << (tentativa - 1));
		long jitter = ThreadLocalRandom.current().nextLong(espera.toMillis() / 5 + 1);
		try {
			Thread.sleep(base + jitter);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

}
