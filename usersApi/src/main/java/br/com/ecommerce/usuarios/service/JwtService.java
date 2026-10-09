package br.com.ecommerce.usuarios.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import br.com.ecommerce.usuarios.entity.Usuario;

@Service
public class JwtService {

	private final JwtEncoder encoder;
	private final Duration expiracao;

	public JwtService(JwtEncoder encoder, @Value("${jwt.expiracao}") Duration expiracao) {
		this.encoder = encoder;
		this.expiracao = expiracao;
	}

	// Token com id (sub), e-mail e perfil, como pede o enunciado
	public String gerarToken(Usuario usuario) {
		Instant agora = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("usuarios")
				.issuedAt(agora)
				.expiresAt(agora.plus(expiracao))
				.subject(usuario.getId().toString())
				.claim("email", usuario.getEmail())
				.claim("perfil", usuario.getPerfil().valor())
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
