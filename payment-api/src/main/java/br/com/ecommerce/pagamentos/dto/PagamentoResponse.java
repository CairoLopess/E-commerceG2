package br.com.ecommerce.pagamentos.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.ecommerce.pagamentos.entity.Pagamento;
import br.com.ecommerce.pagamentos.entity.StatusPagamento;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PagamentoResponse(UUID id, UUID pedidoId, UUID usuarioId, BigDecimal valor, String forma,
		String cartaoFinal, StatusPagamento status, int tentativas, String codigoTransacao, String motivo,
		Instant criadoEm, Instant atualizadoEm) {

	public static PagamentoResponse from(Pagamento p) {
		return new PagamentoResponse(p.id(), p.pedidoId(), p.usuarioId(), p.valor(), p.forma(), p.cartaoFinal(),
				p.status(), p.tentativas(), p.codigoTransacao(), p.motivo(), p.criadoEm(), p.atualizadoEm());
	}

}
