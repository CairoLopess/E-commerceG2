package br.com.ecommerce.pedidos.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pedidos.dto.CarrinhoItemRequest;
import br.com.ecommerce.pedidos.dto.CarrinhoItemResponse;
import br.com.ecommerce.pedidos.dto.CarrinhoResponse;
import br.com.ecommerce.pedidos.security.UsuarioLogado;
import br.com.ecommerce.pedidos.service.CarrinhoService;
import jakarta.validation.Valid;

// Todas as rotas são de logado: o gateway só deixa passar com token válido (jwt no kong.yml)
@RestController
@RequestMapping("/carrinho")
public class CarrinhoController {

	private final CarrinhoService carrinhoService;

	public CarrinhoController(CarrinhoService carrinhoService) {
		this.carrinhoService = carrinhoService;
	}

	@GetMapping
	public CarrinhoResponse ver(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@RequestHeader(value = UsuarioLogado.REQUEST_ID, required = false) String requestId) {
		return carrinhoService.ver(usuarioId, requestId);
	}

	@PostMapping("/itens")
	@ResponseStatus(HttpStatus.CREATED)
	public CarrinhoItemResponse adicionar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@RequestHeader(value = UsuarioLogado.REQUEST_ID, required = false) String requestId,
			@Valid @RequestBody CarrinhoItemRequest request) {
		return carrinhoService.adicionar(usuarioId, request, requestId);
	}

	@DeleteMapping("/itens/{produtoId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@RequestHeader(UsuarioLogado.ID) UUID usuarioId, @PathVariable String produtoId) {
		carrinhoService.remover(usuarioId, produtoId);
	}

}
