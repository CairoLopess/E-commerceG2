package br.com.ecommerce.pedidos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.ecommerce.pedidos.entity.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, UUID> {

	List<Pedido> findByUsuarioIdOrderByCriadoEmDesc(UUID usuarioId);

	// Pedido de outra pessoa é tratado como inexistente (404, não 403)
	Optional<Pedido> findByIdAndUsuarioId(UUID id, UUID usuarioId);

}
