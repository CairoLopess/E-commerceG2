package br.com.ecommerce.usuarios.service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.ecommerce.usuarios.dto.EnderecoRequest;
import br.com.ecommerce.usuarios.dto.EnderecoResponse;
import br.com.ecommerce.usuarios.entity.Endereco;
import br.com.ecommerce.usuarios.entity.Usuario;
import br.com.ecommerce.usuarios.exception.ApiException;
import br.com.ecommerce.usuarios.repository.EnderecoRepository;

@Service
public class EnderecoService {

	private final EnderecoRepository enderecoRepository;
	private final UsuarioService usuarioService;

	public EnderecoService(EnderecoRepository enderecoRepository, UsuarioService usuarioService) {
		this.enderecoRepository = enderecoRepository;
		this.usuarioService = usuarioService;
	}

	@Transactional(readOnly = true)
	public List<EnderecoResponse> listar(UUID usuarioId) {
		return enderecoRepository.findByUsuarioIdOrderByPrincipalDesc(usuarioId).stream()
				.map(EnderecoResponse::from)
				.toList();
	}

	@Transactional
	public EnderecoResponse criar(UUID usuarioId, EnderecoRequest request) {
		Usuario usuario = usuarioService.buscarEntidade(usuarioId);
		boolean principal = Boolean.TRUE.equals(request.principal());
		// Só um endereço principal por usuário
		if (principal) {
			enderecoRepository.desmarcarPrincipal(usuarioId);
		}
		Endereco endereco = new Endereco(usuario, request.apelido(), request.cep(), request.logradouro(),
				request.numero(), request.complemento(), request.cidade(),
				request.uf().toUpperCase(Locale.ROOT), principal);
		return EnderecoResponse.from(enderecoRepository.save(endereco));
	}

	@Transactional
	public void remover(UUID usuarioId, UUID enderecoId) {
		Endereco endereco = enderecoRepository.findByIdAndUsuarioId(enderecoId, usuarioId)
				.orElseThrow(() -> ApiException.naoEncontrado("Endereço não encontrado."));
		enderecoRepository.delete(endereco);
	}

}
