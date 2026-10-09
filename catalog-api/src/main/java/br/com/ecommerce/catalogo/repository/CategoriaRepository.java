package br.com.ecommerce.catalogo.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import br.com.ecommerce.catalogo.document.Categoria;

public interface CategoriaRepository extends MongoRepository<Categoria, String> {

	boolean existsBySlug(String slug);

}
