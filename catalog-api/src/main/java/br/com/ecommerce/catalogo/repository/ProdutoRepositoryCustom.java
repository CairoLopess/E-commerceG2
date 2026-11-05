package br.com.ecommerce.catalogo.repository;

import java.util.List;

import br.com.ecommerce.catalogo.document.Produto;

public interface ProdutoRepositoryCustom {

	// Filtros opcionais: categoria exata, busca no nome (sem diferenciar maiúsculas) e lista de ids
	List<Produto> buscarAtivos(String categoria, String busca, List<String> ids);

	/**
	 * Reserva a quantidade se houver: disponivel diminui, reservado aumenta, num update só e com
	 * o "disponivel >= quantidade" no filtro. Dois pedidos ao mesmo tempo não deixam o estoque
	 * negativo. Devolve false se não deu (produto inativo, inexistente ou sem estoque).
	 */
	boolean reservar(String produtoId, int quantidade);

	// O contrário do reservar: devolve ao disponível o que tinha sido reservado
	void devolverReserva(String produtoId, int quantidade);

}
