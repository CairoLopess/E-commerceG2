package br.com.ecommerce.pagamentos.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.pagamentos.dto.PagamentoResponse;
import br.com.ecommerce.pagamentos.security.UsuarioLogado;
import br.com.ecommerce.pagamentos.service.PagamentoService;

/**
 * Só leitura: o pagamento nasce e muda por eventos, ninguém cobra pela API.
 * O Kong deixa passar qualquer perfil logado; quem confere dono e admin é o serviço.
 */
@RestController
@RequestMapping("/pagamentos")
public class PagamentoController {

	private final PagamentoService pagamentos;

	public PagamentoController(PagamentoService pagamentos) {
		this.pagamentos = pagamentos;
	}

	// O pagamento de um pedido: do dono ou de um admin (404 para os outros)
	@GetMapping("/{pedidoId}")
	public PagamentoResponse buscar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@RequestHeader(value = UsuarioLogado.PERFIL, required = false) String perfil,
			@PathVariable String pedidoId) {
		return pagamentos.buscar(usuarioId, UsuarioLogado.ADMIN.equals(perfil), pedidoId);
	}

	// Todos os pagamentos: só admin (403 para cliente)
	@GetMapping
	public List<PagamentoResponse> listar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@RequestHeader(value = UsuarioLogado.PERFIL, required = false) String perfil) {
		return pagamentos.listar(UsuarioLogado.ADMIN.equals(perfil));
	}

}
