package br.com.ecommerce.pedidos.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pedidos.dto.CheckoutRequest;
import br.com.ecommerce.pedidos.dto.PedidoResponse;
import br.com.ecommerce.pedidos.security.UsuarioLogado;
import br.com.ecommerce.pedidos.service.PedidoService;
import jakarta.validation.Valid;

// Todas as rotas são de logado: o gateway só deixa passar com token válido (jwt no kong.yml)
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

	private final PedidoService pedidoService;

	public PedidoController(PedidoService pedidoService) {
		this.pedidoService = pedidoService;
	}

	/**
	 * 202 e não 201: o pedido foi aceito, mas ainda não terminou. Falta reservar o estoque e
	 * cobrar; hoje ele fica em AGUARDANDO_ESTOQUE, e o Kafka o faz andar na próxima aula.
	 */
	@PostMapping
	public ResponseEntity<PedidoResponse> finalizar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@RequestHeader(UsuarioLogado.EMAIL) String email,
			@RequestHeader(value = UsuarioLogado.REQUEST_ID, required = false) String requestId,
			@Valid @RequestBody CheckoutRequest request) {
		PedidoResponse pedido = pedidoService.finalizar(usuarioId, email, request, requestId);
		return ResponseEntity.accepted().location(URI.create("/pedidos/" + pedido.id())).body(pedido);
	}

	@GetMapping
	public List<PedidoResponse> listar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId) {
		return pedidoService.listar(usuarioId);
	}

	@GetMapping("/{id}")
	public PedidoResponse buscar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId, @PathVariable UUID id) {
		return pedidoService.buscar(usuarioId, id);
	}

	@PostMapping("/{id}/cancelar")
	public PedidoResponse cancelar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId, @PathVariable UUID id) {
		return pedidoService.cancelar(usuarioId, id);
	}

}
