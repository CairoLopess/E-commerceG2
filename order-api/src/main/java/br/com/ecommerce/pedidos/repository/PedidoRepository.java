package br.com.ecommerce.pedidos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import br.com.ecommerce.pedidos.entity.Pedido;
import br.com.ecommerce.pedidos.entity.StatusPedido;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

	List<Pedido> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

	// Pedido de outra pessoa é tratado como inexistente (404, não 403)
	Optional<Pedido> findByIdAndUsuarioId(UUID id, UUID usuarioId);

	/**
	 * Muda o status só se o pedido ainda estiver num dos status de origem, num UPDATE só:
	 * UPDATE pedidos SET status = ? WHERE id = ? AND status IN (...). Devolve quantos mudaram
	 * (0 = já não estava lá: evento repetido, atrasado, ou outra mudança chegou antes).
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			update Pedido p set p.status = :novo, p.motivoCancelamento = :motivo
			where p.id = :id and p.status in :origens""")
	int mudarStatus(@Param("id") UUID id, @Param("novo") StatusPedido novo,
			@Param("origens") Collection<StatusPedido> origens, @Param("motivo") String motivo);

}
