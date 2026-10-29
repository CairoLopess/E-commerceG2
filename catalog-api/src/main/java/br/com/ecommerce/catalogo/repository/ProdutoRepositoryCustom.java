package br.com.ecommerce.catalogo.repository;

import java.util.List;

import br.com.ecommerce.catalogo.document.Produto;

public interface ProdutoRepositoryCustom {

	// Filtros opcionais: categoria exata, busca no nome (sem diferenciar maiúsculas) e lista de ids
	List<Produto> buscarAtivos(String categoria, String busca, List<String> ids);

}
