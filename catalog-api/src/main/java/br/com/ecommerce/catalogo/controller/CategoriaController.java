package br.com.ecommerce.catalogo.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.ecommerce.catalogo.cache.VitrineCache;
import br.com.ecommerce.catalogo.dto.CategoriaRequest;
import br.com.ecommerce.catalogo.dto.CategoriaResponse;
import br.com.ecommerce.catalogo.service.CategoriaService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/categorias")
public class CategoriaController {

	private final CategoriaService categoriaService;
	private final VitrineCache cache;

	public CategoriaController(CategoriaService categoriaService, VitrineCache cache) {
		this.categoriaService = categoriaService;
		this.cache = cache;
	}

	// Pela vitrine: X-Cache HIT, MISS ou OFF
	@GetMapping
	public ResponseEntity<String> listar() {
		return cache.lerOuBuscar("categorias", categoriaService::listar).http();
	}

	// admin (o gateway confere o perfil)
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CategoriaResponse criar(@Valid @RequestBody CategoriaRequest request) {
		return categoriaService.criar(request);
	}

}
