package br.com.ecommerce.catalogo.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.com.ecommerce.catalogo.document.Categoria;
import br.com.ecommerce.catalogo.dto.CategoriaRequest;
import br.com.ecommerce.catalogo.dto.CategoriaResponse;
import br.com.ecommerce.catalogo.exception.ApiException;
import br.com.ecommerce.catalogo.repository.CategoriaRepository;

@Service
public class CategoriaService {

	private final CategoriaRepository categoriaRepository;

	public CategoriaService(CategoriaRepository categoriaRepository) {
		this.categoriaRepository = categoriaRepository;
	}

	public List<CategoriaResponse> listar() {
		return categoriaRepository.findAll(Sort.by("nome")).stream()
				.map(CategoriaResponse::from)
				.toList();
	}

	public CategoriaResponse criar(CategoriaRequest request) {
		if (categoriaRepository.existsBySlug(request.slug())) {
			throw ApiException.conflito("Já existe uma categoria com este slug.");
		}
		return CategoriaResponse.from(categoriaRepository.save(new Categoria(request.nome(), request.slug())));
	}

}
