package br.com.ecommerce.catalogo.dto;

import java.math.BigDecimal;
import java.util.Map;

import br.com.ecommerce.catalogo.document.Estoque;
import br.com.ecommerce.catalogo.document.Produto;

public record ProdutoResponse(String id, String sku, String nome, String categoria, BigDecimal preco,
		boolean ativo, Map<String, Object> atributos, Estoque estoque) {

	public static ProdutoResponse from(Produto produto) {
		return new ProdutoResponse(produto.getId(), produto.getSku(), produto.getNome(), produto.getCategoria(),
				produto.getPreco(), produto.isAtivo(), produto.getAtributos(), produto.getEstoque());
	}

}
