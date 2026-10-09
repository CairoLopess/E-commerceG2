package br.com.ecommerce.catalogo.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.com.ecommerce.catalogo.document.Estoque;
import br.com.ecommerce.catalogo.document.Produto;
import br.com.ecommerce.catalogo.dto.EstoqueRequest;
import br.com.ecommerce.catalogo.dto.ProdutoRequest;
import br.com.ecommerce.catalogo.dto.ProdutoResponse;
import br.com.ecommerce.catalogo.exception.ApiException;
import br.com.ecommerce.catalogo.repository.CategoriaRepository;
import br.com.ecommerce.catalogo.repository.ProdutoRepository;

@Service
public class ProdutoService {

	private static final String SKU_DUPLICADO = "Já existe um produto com este SKU.";
	private static final String NAO_ENCONTRADO = "Produto não encontrado.";

	private final ProdutoRepository produtoRepository;
	private final CategoriaRepository categoriaRepository;

	public ProdutoService(ProdutoRepository produtoRepository, CategoriaRepository categoriaRepository) {
		this.produtoRepository = produtoRepository;
		this.categoriaRepository = categoriaRepository;
	}

	// Vitrine pública: só produtos ativos
	public List<ProdutoResponse> listar(String categoria, String busca) {
		return produtoRepository.buscarAtivos(categoria, busca).stream()
				.map(ProdutoResponse::from)
				.toList();
	}

	public ProdutoResponse buscar(String id) {
		return produtoRepository.findByIdAndAtivoTrue(id)
				.map(ProdutoResponse::from)
				.orElseThrow(() -> ApiException.naoEncontrado(NAO_ENCONTRADO));
	}

	public ProdutoResponse criar(ProdutoRequest request) {
		validarCategoria(request.categoria());
		if (produtoRepository.existsBySku(request.sku())) {
			throw ApiException.conflito(SKU_DUPLICADO);
		}
		int disponivel = request.estoque() != null ? request.estoque().disponivel() : 0;
		Produto produto = new Produto(request.sku(), request.nome(), request.categoria(), request.preco(),
				atributos(request), new Estoque(disponivel, 0));
		if (request.ativo() != null) {
			produto.setAtivo(request.ativo());
		}
		return ProdutoResponse.from(produtoRepository.save(produto));
	}

	// PUT substitui os dados do produto; o "reservado" do estoque nunca é mexido aqui
	public ProdutoResponse atualizar(String id, ProdutoRequest request) {
		Produto produto = buscarEntidade(id);
		validarCategoria(request.categoria());
		if (!request.sku().equals(produto.getSku()) && produtoRepository.existsBySku(request.sku())) {
			throw ApiException.conflito(SKU_DUPLICADO);
		}
		produto.setSku(request.sku());
		produto.setNome(request.nome());
		produto.setCategoria(request.categoria());
		produto.setPreco(request.preco());
		produto.setAtributos(atributos(request));
		if (request.estoque() != null) {
			produto.setEstoque(produto.getEstoque().comDisponivel(request.estoque().disponivel()));
		}
		if (request.ativo() != null) {
			produto.setAtivo(request.ativo());
		}
		return ProdutoResponse.from(produtoRepository.save(produto));
	}

	public ProdutoResponse ajustarEstoque(String id, EstoqueRequest request) {
		Produto produto = buscarEntidade(id);
		produto.setEstoque(produto.getEstoque().comDisponivel(request.disponivel()));
		return ProdutoResponse.from(produtoRepository.save(produto));
	}

	// DELETE não apaga: só tira da vitrine (ativo = false)
	public void desativar(String id) {
		Produto produto = buscarEntidade(id);
		produto.setAtivo(false);
		produtoRepository.save(produto);
	}

	// Rotas de admin enxergam também os produtos desativados
	private Produto buscarEntidade(String id) {
		return produtoRepository.findById(id).orElseThrow(() -> ApiException.naoEncontrado(NAO_ENCONTRADO));
	}

	private void validarCategoria(String slug) {
		if (!categoriaRepository.existsBySlug(slug)) {
			throw ApiException.invalido("Categoria '" + slug + "' não existe.");
		}
	}

	private static Map<String, Object> atributos(ProdutoRequest request) {
		return request.atributos() != null ? new HashMap<>(request.atributos()) : new HashMap<>();
	}

}
