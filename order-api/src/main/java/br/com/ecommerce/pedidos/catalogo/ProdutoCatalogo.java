package br.com.ecommerce.pedidos.catalogo;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Só os campos do produto que o pedidos usa; o resto da resposta do catálogo é ignorado
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProdutoCatalogo(String id, String nome, BigDecimal preco, Estoque estoque) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Estoque(int disponivel) {
	}

	public boolean temEstoque(int quantidade) {
		return estoque != null && estoque.disponivel() >= quantidade;
	}

}
