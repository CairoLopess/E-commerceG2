package br.com.ecommerce.usuarios.controller;

import java.util.List;
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

import br.com.ecommerce.usuarios.dto.EnderecoRequest;
import br.com.ecommerce.usuarios.dto.EnderecoResponse;
import br.com.ecommerce.usuarios.security.UsuarioLogado;
import br.com.ecommerce.usuarios.service.EnderecoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios/me/enderecos")
public class EnderecoController {

	private final EnderecoService enderecoService;

	public EnderecoController(EnderecoService enderecoService) {
		this.enderecoService = enderecoService;
	}

	@GetMapping
	public List<EnderecoResponse> listar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId) {
		return enderecoService.listar(usuarioId);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public EnderecoResponse criar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@Valid @RequestBody EnderecoRequest request) {
		return enderecoService.criar(usuarioId, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void remover(@RequestHeader(UsuarioLogado.ID) UUID usuarioId, @PathVariable UUID id) {
		enderecoService.remover(usuarioId, id);
	}

}
