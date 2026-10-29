package br.com.ecommerce.usuarios.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.usuarios.dto.UsuarioCreateRequest;
import br.com.ecommerce.usuarios.dto.UsuarioResponse;
import br.com.ecommerce.usuarios.dto.UsuarioUpdateRequest;
import br.com.ecommerce.usuarios.security.UsuarioLogado;
import br.com.ecommerce.usuarios.service.UsuarioService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/usuarios")
public class UsuarioController {

	private final UsuarioService usuarioService;

	public UsuarioController(UsuarioService usuarioService) {
		this.usuarioService = usuarioService;
	}

	// público
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UsuarioResponse criar(@Valid @RequestBody UsuarioCreateRequest request) {
		return usuarioService.criar(request);
	}

	// admin: o gateway só deixa passar token com perfil admin (jwt + acl no kong.yml)
	@GetMapping
	public List<UsuarioResponse> listar() {
		return usuarioService.listar();
	}

	@GetMapping("/me")
	public UsuarioResponse meusDados(@RequestHeader(UsuarioLogado.ID) UUID usuarioId) {
		return usuarioService.buscar(usuarioId);
	}

	@PutMapping("/me")
	public UsuarioResponse atualizar(@RequestHeader(UsuarioLogado.ID) UUID usuarioId,
			@Valid @RequestBody UsuarioUpdateRequest request) {
		return usuarioService.atualizar(usuarioId, request);
	}

}
