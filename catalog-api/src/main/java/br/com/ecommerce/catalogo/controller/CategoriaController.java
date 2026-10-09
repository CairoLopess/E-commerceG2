package br.com.ecommerce.catalogo.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.catalogo.dto.CategoriaRequest;
import br.com.ecommerce.catalogo.dto.CategoriaResponse;
import br.com.ecommerce.catalogo.service.CategoriaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

	private final CategoriaService categoriaService;

	public CategoriaController(CategoriaService categoriaService) {
		this.categoriaService = categoriaService;
	}

	@GetMapping
	public List<CategoriaResponse> listar() {
		return categoriaService.listar();
	}

	// admin (aberta até o gateway chegar)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoriaResponse criar(@Valid @RequestBody CategoriaRequest request) {
		return categoriaService.criar(request);
	}

}
