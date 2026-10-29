package br.com.ecommerce.pedidos.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.ecommerce.pedidos.entity.CarrinhoItem;

public interface CarrinhoItemRepository extends JpaRepository<CarrinhoItem, CarrinhoItem.Chave> {

	List<CarrinhoItem> findByUsuarioIdOrderByProdutoId(UUID usuarioId);

	Optional<CarrinhoItem> findByUsuarioIdAndProdutoId(UUID usuarioId, String produtoId);

	void deleteByUsuarioId(UUID usuarioId);

}
