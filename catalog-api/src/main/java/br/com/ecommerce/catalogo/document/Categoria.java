package br.com.ecommerce.catalogo.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("categorias")
public class Categoria {

	@Id
	private String id;

	private String nome;

	@Indexed(unique = true)
	private String slug;

	protected Categoria() {
	}

	public Categoria(String nome, String slug) {
		this.nome = nome;
		this.slug = slug;
	}

	public String getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getSlug() {
		return slug;
	}

}
