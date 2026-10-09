package br.com.ecommerce.catalogo.repository;

import java.util.List;

import br.com.ecommerce.catalogo.document.Produto;

public interface ProdutoRepositoryCustom {

	// Filtros opcionais: categoria exata e busca no nome (sem diferenciar maiúsculas)
	List<Produto> buscarAtivos(String categoria, String busca);

}
