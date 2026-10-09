package br.com.ecommerce.usuarios.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

	// admin (regra no SecurityConfig)
	@GetMapping
	public List<UsuarioResponse> listar() {
		return usuarioService.listar();
	}

	@GetMapping("/me")
	public UsuarioResponse meusDados(@AuthenticationPrincipal Jwt jwt) {
		return usuarioService.buscar(UsuarioLogado.id(jwt));
	}

	@PutMapping("/me")
	public UsuarioResponse atualizar(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody UsuarioUpdateRequest request) {
		return usuarioService.atualizar(UsuarioLogado.id(jwt), request);
	}

}
