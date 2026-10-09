package br.com.ecommerce.catalogo.dto;

import br.com.ecommerce.catalogo.document.Categoria;

public record CategoriaResponse(String id, String nome, String slug) {

	public static CategoriaResponse from(Categoria categoria) {
		return new CategoriaResponse(categoria.getId(), categoria.getNome(), categoria.getSlug());
	}

}
