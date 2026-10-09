package br.com.ecommerce.usuarios.dto;

import java.time.Instant;
import java.util.UUID;

import br.com.ecommerce.usuarios.entity.Usuario;

// A senha (nem o hash) nunca sai da API
public record UsuarioResponse(UUID id, String nome, String email, String perfil, Instant criadoEm) {

	public static UsuarioResponse from(Usuario usuario) {
		return new UsuarioResponse(usuario.getId(), usuario.getNome(), usuario.getEmail(),
				usuario.getPerfil().valor(), usuario.getCriadoEm());
	}

}
