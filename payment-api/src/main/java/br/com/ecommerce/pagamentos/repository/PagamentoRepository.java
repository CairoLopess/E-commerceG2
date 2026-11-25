package br.com.ecommerce.pagamentos.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import br.com.ecommerce.pagamentos.entity.Pagamento;
import br.com.ecommerce.pagamentos.entity.StatusPagamento;

/**
 * SQL à mão (JdbcTemplate): a idempotência está no próprio SQL.
 *
 * - inserir: INSERT ... ON CONFLICT DO NOTHING. O pedido_id UNIQUE faz dois papéis: não cobrar
 *   duas vezes (estoque.reservado repetido) e não cobrar o que foi cancelado antes.
 * - toda mudança de status leva o status de origem no WHERE: devolve 0 se o pagamento já saiu dele
 *   (evento repetido, ou um cancelamento chegou no meio da cobrança).
 */
@Repository
public class PagamentoRepository {

	private static final String COLUNAS = """
			id, pedido_id, usuario_id, valor, forma, cartao_final, status, tentativas, codigo_transacao, motivo,
			criado_em, atualizado_em""";

	private final JdbcTemplate jdbc;

	public PagamentoRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	// 1 = gravou (era novo); 0 = já existia um pagamento para esse pedido
	public int inserir(UUID pedidoId, UUID usuarioId, BigDecimal valor, String forma, String cartaoFinal,
			StatusPagamento status, String motivo) {
		return jdbc.update("""
				INSERT INTO pagamentos (id, pedido_id, usuario_id, valor, forma, cartao_final, status, motivo)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT DO NOTHING""",
				UUID.randomUUID(), pedidoId, usuarioId, valor, forma, cartaoFinal, status.name(), motivo);
	}

	// O resultado da cobrança, só se ainda estiver PENDENTE (o cliente pode ter cancelado no meio)
	public int concluir(UUID pedidoId, StatusPagamento status, int tentativas, String codigo, String motivo) {
		return jdbc.update("""
				UPDATE pagamentos
				SET status = ?, tentativas = ?, codigo_transacao = ?, motivo = ?, atualizado_em = CURRENT_TIMESTAMP
				WHERE pedido_id = ? AND status = 'PENDENTE'""",
				status.name(), tentativas, codigo, motivo, pedidoId);
	}

	// Cancelado com a cobrança em andamento: o resultado que chegar depois não vale (é estornado)
	public int cancelarPendente(UUID pedidoId, String motivo) {
		return jdbc.update("""
				UPDATE pagamentos SET status = 'CANCELADO', motivo = ?, atualizado_em = CURRENT_TIMESTAMP
				WHERE pedido_id = ? AND status = 'PENDENTE'""",
				motivo, pedidoId);
	}

	public int marcarEstornado(UUID pedidoId, StatusPagamento origem, String codigo, int tentativas, String motivo) {
		return jdbc.update("""
				UPDATE pagamentos
				SET status = 'ESTORNADO', codigo_transacao = COALESCE(?, codigo_transacao),
				    tentativas = GREATEST(tentativas, ?), motivo = ?, atualizado_em = CURRENT_TIMESTAMP
				WHERE pedido_id = ? AND status = ?""",
				codigo, tentativas, motivo, pedidoId, origem.name());
	}

	public Optional<Pagamento> buscar(UUID pedidoId) {
		return jdbc.query("SELECT " + COLUNAS + " FROM pagamentos WHERE pedido_id = ?", PagamentoRepository::linha,
				pedidoId).stream().findFirst();
	}

	public List<Pagamento> listar() {
		return jdbc.query("SELECT " + COLUNAS + " FROM pagamentos ORDER BY criado_em DESC", PagamentoRepository::linha);
	}

	private static Pagamento linha(ResultSet rs, int numero) throws SQLException {
		return new Pagamento(rs.getObject("id", UUID.class), rs.getObject("pedido_id", UUID.class),
				rs.getObject("usuario_id", UUID.class), rs.getBigDecimal("valor"), rs.getString("forma"),
				rs.getString("cartao_final"), StatusPagamento.valueOf(rs.getString("status")), rs.getInt("tentativas"),
				rs.getString("codigo_transacao"), rs.getString("motivo"),
				rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
				rs.getObject("atualizado_em", OffsetDateTime.class).toInstant());
	}

}
