package br.com.ecommerce.catalogo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.catalogo.dto.EstoqueRequest;
import br.com.ecommerce.catalogo.dto.ProdutoRequest;
import br.com.ecommerce.catalogo.dto.ProdutoResponse;
import br.com.ecommerce.catalogo.service.ProdutoService;
import jakarta.validation.Valid;

/**
 * Rotas de admin (POST, PUT, PATCH, DELETE) ficam abertas nesta aula: quem vai
 * validar o token e o perfil é o gateway, na aula 2.
 */
@RestController
@RequestMapping("/produtos")
public class ProdutoController {

	private final ProdutoService produtoService;

	public ProdutoController(ProdutoService produtoService) {
		this.produtoService = produtoService;
	}

	@GetMapping
	public List<ProdutoResponse> listar(@RequestParam(required = false) String categoria,
			@RequestParam(required = false) String busca) {
		return produtoService.listar(categoria, busca);
	}

	@GetMapping("/{id}")
	public ProdutoResponse buscar(@PathVariable String id) {
		return produtoService.buscar(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProdutoResponse criar(@Valid @RequestBody ProdutoRequest request) {
		return produtoService.criar(request);
	}

	@PutMapping("/{id}")
	public ProdutoResponse atualizar(@PathVariable String id, @Valid @RequestBody ProdutoRequest request) {
		return produtoService.atualizar(id, request);
	}

	@PatchMapping("/{id}/estoque")
	public ProdutoResponse ajustarEstoque(@PathVariable String id, @Valid @RequestBody EstoqueRequest request) {
		return produtoService.ajustarEstoque(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void desativar(@PathVariable String id) {
		produtoService.desativar(id);
	}

}
