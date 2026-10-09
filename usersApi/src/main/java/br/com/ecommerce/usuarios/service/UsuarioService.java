package br.com.ecommerce.usuarios.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecommerce.usuarios.dto.UsuarioCreateRequest;
import br.com.ecommerce.usuarios.dto.UsuarioResponse;
import br.com.ecommerce.usuarios.dto.UsuarioUpdateRequest;
import br.com.ecommerce.usuarios.entity.Perfil;
import br.com.ecommerce.usuarios.entity.Usuario;
import br.com.ecommerce.usuarios.exception.ApiException;
import br.com.ecommerce.usuarios.repository.UsuarioRepository;

@Service
public class UsuarioService {

	private static final String EMAIL_DUPLICADO = "Este e-mail já está cadastrado.";

	private final UsuarioRepository usuarioRepository;
	private final PasswordEncoder passwordEncoder;

	public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
		this.usuarioRepository = usuarioRepository;
		this.passwordEncoder = passwordEncoder;
	}

	static String normalizarEmail(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

	// Cadastro público: sempre nasce como cliente
	@Transactional
	public UsuarioResponse criar(UsuarioCreateRequest request) {
		String email = normalizarEmail(request.email());
		if (usuarioRepository.existsByEmail(email)) {
			throw ApiException.conflito(EMAIL_DUPLICADO);
		}
		Usuario usuario = new Usuario(request.nome().trim(), email, passwordEncoder.encode(request.senha()),
				Perfil.CLIENTE);
		return UsuarioResponse.from(usuarioRepository.save(usuario));
	}

	@Transactional(readOnly = true)
	public UsuarioResponse buscar(UUID id) {
		return UsuarioResponse.from(buscarEntidade(id));
	}

	@Transactional
	public UsuarioResponse atualizar(UUID id, UsuarioUpdateRequest request) {
		Usuario usuario = buscarEntidade(id);
		String email = normalizarEmail(request.email());
		if (!email.equals(usuario.getEmail()) && usuarioRepository.existsByEmail(email)) {
			throw ApiException.conflito(EMAIL_DUPLICADO);
		}
		usuario.setNome(request.nome().trim());
		usuario.setEmail(email);
		if (request.senha() != null) {
			usuario.setSenhaHash(passwordEncoder.encode(request.senha()));
		}
		return UsuarioResponse.from(usuario);
	}

	@Transactional(readOnly = true)
	public List<UsuarioResponse> listar() {
		return usuarioRepository.findAll(Sort.by("criadoEm")).stream()
				.map(UsuarioResponse::from)
				.toList();
	}

	// Token válido de um usuário que não existe mais: tratamos como não autenticado
	Usuario buscarEntidade(UUID id) {
		return usuarioRepository.findById(id)
				.orElseThrow(() -> ApiException.naoAutenticado("Usuário do token não existe mais."));
	}

}
