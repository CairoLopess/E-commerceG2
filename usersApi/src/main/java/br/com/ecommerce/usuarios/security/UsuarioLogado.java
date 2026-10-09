package br.com.ecommerce.usuarios.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

public final class UsuarioLogado {

	private UsuarioLogado() {
	}

	// O "sub" do token é o id do usuário (ver JwtService)
	public static UUID id(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

}
