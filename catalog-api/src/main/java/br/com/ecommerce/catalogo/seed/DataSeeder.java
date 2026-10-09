package br.com.ecommerce.catalogo.seed;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import br.com.ecommerce.catalogo.document.Categoria;
import br.com.ecommerce.catalogo.document.Estoque;
import br.com.ecommerce.catalogo.document.Produto;
import br.com.ecommerce.catalogo.repository.CategoriaRepository;
import br.com.ecommerce.catalogo.repository.ProdutoRepository;

/**
 * Seed obrigatório: 3 categorias e pelo menos 10 produtos, com atributos diferentes por categoria.
 * Idempotente: só insere o slug/SKU que ainda não existe, então rodar de novo não duplica.
 */
@Component
public class DataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

	private static final List<Categoria> CATEGORIAS = List.of(
			new Categoria("Celulares", "celulares"),
			new Categoria("Roupas", "roupas"),
			new Categoria("Livros", "livros"));

	private final CategoriaRepository categoriaRepository;
	private final ProdutoRepository produtoRepository;

	public DataSeeder(CategoriaRepository categoriaRepository, ProdutoRepository produtoRepository) {
		this.categoriaRepository = categoriaRepository;
		this.produtoRepository = produtoRepository;
	}

	@Override
	public void run(ApplicationArguments args) {
		int categorias = 0;
		for (Categoria categoria : CATEGORIAS) {
			if (!categoriaRepository.existsBySlug(categoria.getSlug())) {
				categoriaRepository.save(new Categoria(categoria.getNome(), categoria.getSlug()));
				categorias++;
			}
		}

		int produtos = 0;
		for (Produto produto : produtos()) {
			if (!produtoRepository.existsBySku(produto.getSku())) {
				produtoRepository.save(produto);
				produtos++;
			}
		}

		log.info("Seed: {} categorias e {} produtos inseridos", categorias, produtos);
	}

	// Criados a cada execução: o save preenche o id, então não dá para reaproveitar instâncias
	private static List<Produto> produtos() {
		return List.of(
				// celulares: memória e cor
				produto("CEL-001", "Smartphone X", "celulares", "2499.90", 10,
						Map.of("memoria", "128GB", "cor", "preto")),
				produto("CEL-002", "Smartphone X Pro", "celulares", "3999.90", 5,
						Map.of("memoria", "256GB", "cor", "azul")),
				produto("CEL-003", "Smartphone Lite", "celulares", "1299.90", 20,
						Map.of("memoria", "64GB", "cor", "branco")),
				produto("CEL-004", "Smartphone Max", "celulares", "5499.00", 3,
						Map.of("memoria", "512GB", "cor", "grafite")),

				// roupas: tamanho e tecido
				produto("CAM-010", "Camiseta básica", "roupas", "59.90", 40,
						Map.of("tamanho", "M", "tecido", "algodão")),
				produto("CAM-011", "Camisa polo", "roupas", "99.90", 25,
						Map.of("tamanho", "G", "tecido", "piquet")),
				produto("CAL-020", "Calça jeans", "roupas", "179.90", 15,
						Map.of("tamanho", "42", "tecido", "jeans")),
				produto("JAQ-030", "Jaqueta corta-vento", "roupas", "249.90", 8,
						Map.of("tamanho", "M", "tecido", "poliéster")),

				// livros: autor e páginas
				produto("LIV-100", "Java Efetivo", "livros", "129.90", 12,
						Map.of("autor", "Joshua Bloch", "paginas", 412)),
				produto("LIV-101", "Código Limpo", "livros", "99.90", 18,
						Map.of("autor", "Robert C. Martin", "paginas", 425)),
				produto("LIV-102", "Arquitetura Limpa", "livros", "89.90", 9,
						Map.of("autor", "Robert C. Martin", "paginas", 432)),
				produto("LIV-103", "Domain-Driven Design", "livros", "149.90", 6,
						Map.of("autor", "Eric Evans", "paginas", 528)));
	}

	private static Produto produto(String sku, String nome, String categoria, String preco, int disponivel,
			Map<String, Object> atributos) {
		return new Produto(sku, nome, categoria, new BigDecimal(preco), new HashMap<>(atributos),
				new Estoque(disponivel, 0));
	}

}
