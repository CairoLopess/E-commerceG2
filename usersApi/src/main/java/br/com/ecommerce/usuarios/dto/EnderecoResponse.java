package br.com.ecommerce.usuarios.dto;

import java.util.UUID;

import br.com.ecommerce.usuarios.entity.Endereco;

public record EnderecoResponse(UUID id, String apelido, String cep, String logradouro, String numero,
		String complemento, String cidade, String uf, boolean principal) {

	public static EnderecoResponse from(Endereco endereco) {
		return new EnderecoResponse(endereco.getId(), endereco.getApelido(), endereco.getCep(),
				endereco.getLogradouro(), endereco.getNumero(), endereco.getComplemento(), endereco.getCidade(),
				endereco.getUf(), endereco.isPrincipal());
	}

}
