package br.com.ecommerce.pedidos.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import br.com.ecommerce.pedidos.catalogo.CatalogoClient;
import br.com.ecommerce.pedidos.catalogo.CatalogoIndisponivelException;
import br.com.ecommerce.pedidos.catalogo.ProdutoCatalogo;
import br.com.ecommerce.pedidos.dto.CarrinhoItemRequest;
import br.com.ecommerce.pedidos.dto.CarrinhoItemResponse;
import br.com.ecommerce.pedidos.dto.CarrinhoResponse;
import br.com.ecommerce.pedidos.entity.CarrinhoItem;
import br.com.ecommerce.pedidos.exception.ApiException;
import br.com.ecommerce.pedidos.repository.CarrinhoItemRepository;

@Service
public class CarrinhoService {

	private static final String SEM_CATALOGO = "Catálogo indisponível: nomes e preços não puderam ser atualizados.";

	private final CarrinhoItemRepository carrinhoRepository;
	private final CatalogoClient catalogo;

	public CarrinhoService(CarrinhoItemRepository carrinhoRepository, CatalogoClient catalogo) {
		this.carrinhoRepository = carrinhoRepository;
		this.catalogo = catalogo;
	}

	/**
	 * Carrinho com os preços atuais do catálogo. Se o catálogo não responder, o carrinho
	 * continua funcionando: devolve produto e quantidade, sem preço, com um aviso.
	 */
	public CarrinhoResponse ver(UUID usuarioId, String requestId) {
		List<CarrinhoItem> itens = carrinhoRepository.findByUsuarioIdOrderByProdutoId(usuarioId);
		if (itens.isEmpty()) {
			return new CarrinhoResponse(List.of(), BigDecimal.ZERO, null);
		}

		Map<String, ProdutoCatalogo> produtos;
		try {
			produtos = catalogo.buscarProdutos(ids(itens), requestId);
		} catch (CatalogoIndisponivelException ex) {
			return new CarrinhoResponse(itens.stream().map(CarrinhoItemResponse::semCatalogo).toList(), null,
					SEM_CATALOGO);
		}

		List<CarrinhoItemResponse> resposta = itens.stream()
				.map(item -> CarrinhoItemResponse.de(item, produtos.get(item.getProdutoId())))
				.toList();
		BigDecimal total = resposta.stream()
				.map(CarrinhoItemResponse::subtotal)
				.filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CarrinhoResponse(resposta, total, null);
	}

	// Produto que já está no carrinho: soma a quantidade
	public CarrinhoItemResponse adicionar(UUID usuarioId, CarrinhoItemRequest request, String requestId) {
		// Sem FK para o catálogo: quem garante que o produto existe é a pergunta ao dono dele
		ProdutoCatalogo produto = catalogo.buscarProdutos(List.of(request.produtoId()), requestId)
				.get(request.produtoId());
		if (produto == null) {
			throw ApiException.naoEncontrado("Produto não encontrado.");
		}

		CarrinhoItem item = carrinhoRepository.findByUsuarioIdAndProdutoId(usuarioId, request.produtoId())
				.orElseGet(() -> new CarrinhoItem(usuarioId, request.produtoId(), 0));
		item.somar(request.quantidade());
		carrinhoRepository.save(item);
		return CarrinhoItemResponse.de(item, produto);
	}

	public void remover(UUID usuarioId, String produtoId) {
		CarrinhoItem item = carrinhoRepository.findByUsuarioIdAndProdutoId(usuarioId, produtoId)
				.orElseThrow(() -> ApiException.naoEncontrado("Produto não está no carrinho."));
		carrinhoRepository.delete(item);
	}

	static List<String> ids(List<CarrinhoItem> itens) {
		return itens.stream().map(CarrinhoItem::getProdutoId).toList();
	}

}
