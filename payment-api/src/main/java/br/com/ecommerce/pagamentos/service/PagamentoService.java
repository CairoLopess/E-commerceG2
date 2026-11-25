package br.com.ecommerce.pagamentos.service;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.ecommerce.pagamentos.banco.BancoIndisponivelException;
import br.com.ecommerce.pagamentos.banco.CobrancaResiliente;
import br.com.ecommerce.pagamentos.banco.ResultadoCobranca;
import br.com.ecommerce.pagamentos.dto.PagamentoResponse;
import br.com.ecommerce.pagamentos.entity.Pagamento;
import br.com.ecommerce.pagamentos.entity.StatusPagamento;
import br.com.ecommerce.pagamentos.evento.Eventos;
import br.com.ecommerce.pagamentos.evento.KafkaConfig;
import br.com.ecommerce.pagamentos.evento.PagamentoEventos;
import br.com.ecommerce.pagamentos.evento.PagamentoEventos.EstoqueReservado;
import br.com.ecommerce.pagamentos.evento.PagamentoEventos.PedidoCancelado;
import br.com.ecommerce.pagamentos.exception.ApiException;
import br.com.ecommerce.pagamentos.repository.PagamentoRepository;

/**
 * O passo "cobrar o cartão" da saga, e a sua compensação, "estornar".
 *
 *   estoque.reservado -> grava PENDENTE, cobra (timeout + retry + circuit breaker), grava o resultado
 *                        e publica pagamento.aprovado ou pagamento.recusado
 *   pedido.cancelado  -> ainda não cobrou: grava CANCELADO e a cobrança futura é ignorada
 *                        já cobrou (APROVADO): estorna e publica pagamento.estornado
 *
 * ERRO (o banco não respondeu) também publica pagamento.recusado: o pedido não fica parado
 * esperando um banco que caiu. A saga compensa igual; só o motivo muda.
 */
@Service
public class PagamentoService {

	private static final Logger log = LoggerFactory.getLogger(PagamentoService.class);

	private final PagamentoRepository pagamentos;
	private final CobrancaResiliente cobranca;
	private final Eventos eventos;

	public PagamentoService(PagamentoRepository pagamentos, CobrancaResiliente cobranca, Eventos eventos) {
		this.pagamentos = pagamentos;
		this.cobranca = cobranca;
		this.eventos = eventos;
	}

	// ---------- estoque.reservado ----------

	public void cobrar(String pedidoIdTexto, EstoqueReservado dados) {
		UUID pedidoId = uuid(pedidoIdTexto);
		UUID usuarioId = uuid(dados.usuarioId());
		if (pedidoId == null || usuarioId == null || dados.valorTotal() == null) {
			log.warn("estoque.reservado inválido (pedido {}, usuario {}, valor {}): ignorado", pedidoIdTexto,
					dados.usuarioId(), dados.valorTotal());
			return;
		}

		// 1. idempotência: o mesmo evento não cobra duas vezes
		if (pagamentos.inserir(pedidoId, usuarioId, dados.valorTotal(), dados.formaPagamento(), dados.cartaoFinal(),
				StatusPagamento.PENDENTE, null) == 0) {
			Pagamento existente = pagamentos.buscar(pedidoId).orElseThrow();
			switch (existente.status()) {
				case PENDENTE ->
					// Caiu no meio da cobrança da outra vez. Retomar é seguro: o banco reconhece a
					// referência e devolve a mesma cobrança, sem cobrar de novo.
					log.warn("Pedido {}: pagamento PENDENTE de uma tentativa interrompida, retomando a cobrança",
							pedidoId);
				case CANCELADO, ESTORNADO -> {
					log.info("Pedido {}: cancelado antes da cobrança ({}), não cobra", pedidoId, existente.status());
					return;
				}
				default -> {
					// Já tinha resultado: talvez a publicação tenha falhado. Republica (quem escuta aguenta repetido).
					log.info("Pedido {}: já estava {}, republica o resultado", pedidoId, existente.status());
					publicarResultado(existente, dados.clienteEmail());
					return;
				}
			}
		}

		// 2. timeout + retry + circuit breaker
		log.info("Pedido {}: cobrando R$ {} ({}{})", pedidoId, dados.valorTotal(), dados.formaPagamento(),
				dados.cartaoFinal() != null ? " final " + dados.cartaoFinal() : "");
		ResultadoCobranca r = cobranca.cobrar(pedidoId, dados.valorTotal(), dados.formaPagamento(),
				dados.cartaoFinal());

		if (pagamentos.concluir(pedidoId, r.status(), r.tentativas(), r.codigoTransacao(), r.motivo()) == 0) {
			// O cliente cancelou enquanto o banco respondia: o resultado não vale mais
			log.info("Pedido {}: cancelado durante a cobrança (resultado: {})", pedidoId, r.status());
			if (r.aprovado()) {
				estornarECompensar(pedidoId, r.codigoTransacao(), r.tentativas(), StatusPagamento.CANCELADO,
						dados.usuarioId(), dados.clienteEmail());
			} else if (r.houveTimeout()) {
				estornoDeSeguranca(pedidoId);
			}
			return;
		}
		log.info("Pedido {}: pagamento {} em {} tentativa(s){}", pedidoId, r.status(), r.tentativas(),
				r.motivo() != null ? ": " + r.motivo() : "");

		// Desistiu sem saber se o banco cobrou? Desfaz por segurança.
		if (r.status() == StatusPagamento.ERRO && r.houveTimeout()) {
			estornoDeSeguranca(pedidoId);
		}

		// 3. o resultado vira evento
		publicarResultado(pagamentos.buscar(pedidoId).orElseThrow(), dados.clienteEmail());
	}

	private void publicarResultado(Pagamento p, String clienteEmail) {
		String usuarioId = p.usuarioId().toString();
		String pedidoId = p.pedidoId().toString();
		switch (p.status()) {
			case APROVADO -> eventos.publicar(KafkaConfig.TOPICO_PAGAMENTOS, PagamentoEventos.APROVADO, pedidoId,
					new PagamentoEventos.Aprovado(usuarioId, clienteEmail, p.valor(), p.codigoTransacao(),
							p.tentativas()));
			case RECUSADO, ERRO -> eventos.publicar(KafkaConfig.TOPICO_PAGAMENTOS, PagamentoEventos.RECUSADO,
					pedidoId,
					new PagamentoEventos.Recusado(usuarioId, clienteEmail, p.valor(), p.motivo(), p.tentativas()));
			default -> log.info("Pedido {}: status {}, nada a publicar", pedidoId, p.status());
		}
	}

	/**
	 * Timeout é "não sei o que aconteceu": o banco pode ter cobrado e a resposta não chegou.
	 * Pede o estorno da referência; se não havia cobrança, o banco responde 404 e tudo bem.
	 * Se nem o estorno passar, fica o ERROR no log (a conciliação diária pega).
	 */
	private void estornoDeSeguranca(UUID pedidoId) {
		try {
			if (cobranca.estornar(null, pedidoId)) {
				log.warn("Pedido {}: o banco tinha cobrado sem responder a tempo; ESTORNADO por segurança", pedidoId);
			}
		} catch (RuntimeException e) {
			log.error("Pedido {}: ESTORNO DE SEGURANÇA FALHOU ({}): conferir na conciliação com o banco", pedidoId,
					e.getMessage());
		}
	}

	// ---------- pedido.cancelado ----------

	public void aoCancelar(String pedidoIdTexto, PedidoCancelado dados) {
		UUID pedidoId = uuid(pedidoIdTexto);
		UUID usuarioId = uuid(dados.usuarioId());
		if (pedidoId == null || usuarioId == null || dados.valorTotal() == null) {
			log.warn("pedido.cancelado inválido (pedido {}, usuario {}): ignorado", pedidoIdTexto, dados.usuarioId());
			return;
		}

		// Ainda não cobrei? Ocupa o pedido_id com CANCELADO: o estoque.reservado que chegar depois é ignorado
		if (pagamentos.inserir(pedidoId, usuarioId, dados.valorTotal(), dados.formaPagamento(), dados.cartaoFinal(),
				StatusPagamento.CANCELADO, "pedido cancelado antes da cobrança") == 1) {
			log.info("Pedido {}: cancelado antes da cobrança, pagamento bloqueado (CANCELADO)", pedidoId);
			return;
		}
		// Cobrança em andamento (só acontece numa troca de réplica): quem cobra vê o CANCELADO e estorna
		if (pagamentos.cancelarPendente(pedidoId, "pedido cancelado durante a cobrança") == 1) {
			log.info("Pedido {}: cancelado durante a cobrança, o resultado será desfeito", pedidoId);
			return;
		}

		// Já existe: só estorna se foi APROVADO. RECUSADO, ERRO, CANCELADO e ESTORNADO não têm nada a devolver.
		Pagamento pagamento = pagamentos.buscar(pedidoId).orElseThrow();
		if (pagamento.status() != StatusPagamento.APROVADO) {
			log.info("Pedido {}: pagamento {}, nada a estornar", pedidoId, pagamento.status());
			return;
		}
		estornarECompensar(pedidoId, pagamento.codigoTransacao(), pagamento.tentativas(), StatusPagamento.APROVADO,
				dados.usuarioId(), dados.clienteEmail());
	}

	/**
	 * A compensação do pagamento. Precisa dar certo: se o banco não responder, a exceção sobe, o
	 * offset não é confirmado e o Kafka entrega o pedido.cancelado de novo (o estorno é idempotente).
	 * Esgotadas as tentativas do Kafka, fica o ERROR no log e o pagamento continua APROVADO.
	 */
	private void estornarECompensar(UUID pedidoId, String codigo, int tentativas, StatusPagamento origem,
			String usuarioId, String clienteEmail) {
		try {
			cobranca.estornar(codigo, pedidoId);
		} catch (BancoIndisponivelException e) {
			log.error("Pedido {}: ESTORNO FALHOU ({}); o pagamento continua {}", pedidoId, e.getMessage(), origem);
			throw e;
		}
		if (pagamentos.marcarEstornado(pedidoId, origem, codigo, tentativas, "pedido cancelado pelo cliente") == 0) {
			log.info("Pedido {}: já estava estornado", pedidoId);
			return;
		}
		Pagamento estornado = pagamentos.buscar(pedidoId).orElseThrow();
		log.info("Pedido {}: pagamento ESTORNADO ({}, R$ {})", pedidoId, codigo, estornado.valor());
		eventos.publicar(KafkaConfig.TOPICO_PAGAMENTOS, PagamentoEventos.ESTORNADO, pedidoId.toString(),
				new PagamentoEventos.Estornado(usuarioId, clienteEmail, estornado.valor(), codigo));
	}

	// ---------- rotas ----------

	// Do dono ou de um admin. De outra pessoa: 404, não 403 (um 403 confirmaria que existe).
	public PagamentoResponse buscar(UUID usuarioId, boolean admin, String pedidoIdTexto) {
		UUID pedidoId = uuid(pedidoIdTexto);
		return (pedidoId == null ? java.util.Optional.<Pagamento>empty() : pagamentos.buscar(pedidoId))
				.filter(p -> admin || p.usuarioId().equals(usuarioId))
				.map(PagamentoResponse::from)
				.orElseThrow(() -> ApiException.naoEncontrado("Pagamento não encontrado."));
	}

	public List<PagamentoResponse> listar(boolean admin) {
		if (!admin) {
			throw ApiException.proibido("Apenas administradores podem listar todos os pagamentos.");
		}
		return pagamentos.listar().stream().map(PagamentoResponse::from).toList();
	}

	private static UUID uuid(String texto) {
		try {
			return texto == null ? null : UUID.fromString(texto);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
