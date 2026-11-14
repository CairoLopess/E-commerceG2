package br.com.ecommerce.pagamentos.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Uma linha da tabela pagamentos (schema.sql). Um pagamento por pedido: pedido_id é UNIQUE.
 * tentativas e motivo deixam o suporte responder "por que meu pedido foi cancelado?" sem abrir log.
 */
public record Pagamento(UUID id, UUID pedidoId, UUID usuarioId, BigDecimal valor, String forma, String cartaoFinal,
		StatusPagamento status, int tentativas, String codigoTransacao, String motivo, Instant criadoEm,
		Instant atualizadoEm) {
}
