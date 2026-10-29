package br.com.ecommerce.pedidos.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonInclude;

import br.com.ecommerce.pedidos.catalogo.ProdutoCatalogo;
import br.com.ecommerce.pedidos.entity.CarrinhoItem;

/**
 * Item do carrinho com o preço atual do catálogo. O carrinho guarda só produto e quantidade;
 * nome e preço vêm do catálogo a cada consulta (quem copia o preço é o pedido, no checkout).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CarrinhoItemResponse(String produtoId, String nome, BigDecimal precoUnitario, int quantidade,
		BigDecimal subtotal, Boolean disponivel) {

	// produto null: saiu do catálogo (ou foi desativado) depois de entrar no carrinho
	public static CarrinhoItemResponse de(CarrinhoItem item, ProdutoCatalogo produto) {
		if (produto == null) {
			return new CarrinhoItemResponse(item.getProdutoId(), null, null, item.getQuantidade(), null, false);
		}
		BigDecimal subtotal = produto.preco().multiply(BigDecimal.valueOf(item.getQuantidade()));
		return new CarrinhoItemResponse(item.getProdutoId(), produto.nome(), produto.preco(), item.getQuantidade(),
				subtotal, produto.temEstoque(item.getQuantidade()));
	}

	// Catálogo fora do ar: o carrinho continua respondendo, só sem nome e preço
	public static CarrinhoItemResponse semCatalogo(CarrinhoItem item) {
		return new CarrinhoItemResponse(item.getProdutoId(), null, null, item.getQuantidade(), null, null);
	}

}
