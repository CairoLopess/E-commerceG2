package br.com.ecommerce.catalogo.repository;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.ecommerce.catalogo.document.Produto;

public interface ProdutoRepository extends MongoRepository<Produto, String>, ProdutoRepositoryCustom {

	boolean existsBySku(String sku);

	Optional<Produto> findByIdAndAtivoTrue(String id);

}
