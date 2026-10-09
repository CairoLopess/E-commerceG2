package br.com.ecommerce.usuarios.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecommerce.usuarios.dto.LoginRequest;
import br.com.ecommerce.usuarios.dto.LoginResponse;
import br.com.ecommerce.usuarios.dto.UsuarioResponse;
import br.com.ecommerce.usuarios.entity.Usuario;
import br.com.ecommerce.usuarios.exception.ApiException;
import br.com.ecommerce.usuarios.repository.UsuarioRepository;

@Service
public class AuthService {

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		// Mesma mensagem para e-mail inexistente e senha errada: não revela quais e-mails existem
		Usuario usuario = usuarioRepository.findByEmail(UsuarioService.normalizarEmail(request.email()))
				.filter(u -> passwordEncoder.matches(request.senha(), u.getSenhaHash()))
				.orElseThrow(() -> ApiException.naoAutenticado("E-mail ou senha inválidos."));

		return new LoginResponse(jwtService.gerarToken(usuario), UsuarioResponse.from(usuario));
	}

}
