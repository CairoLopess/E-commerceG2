package br.com.ecommerce.catalogo.document;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

/**
 * Cada produto é um documento com os seus próprios atributos: um celular tem memória e cor,
 * uma camiseta tem tamanho e tecido. Por isso o catálogo usa Mongo (ver ADR no README).
 */
@Document("produtos")
public class Produto {

	@Id
	private String id;

	@Indexed(unique = true)
	private String sku;

	private String nome;

	// slug da categoria (ex.: "celulares")
	@Indexed
	private String categoria;

	// Decimal128 no banco: dinheiro não pode virar double
	@Field(targetType = FieldType.DECIMAL128)
	private BigDecimal preco;

	private boolean ativo = true;

	private Map<String, Object> atributos = new HashMap<>();

	private Estoque estoque = new Estoque(0, 0);

	protected Produto() {
	}

	public Produto(String sku, String nome, String categoria, BigDecimal preco, Map<String, Object> atributos,
			Estoque estoque) {
		this.sku = sku;
		this.nome = nome;
		this.categoria = categoria;
		this.preco = preco;
		this.atributos = atributos;
		this.estoque = estoque;
	}

	public String getId() {
		return id;
	}

	public String getSku() {
		return sku;
	}

	public void setSku(String sku) {
		this.sku = sku;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getCategoria() {
		return categoria;
	}

	public void setCategoria(String categoria) {
		this.categoria = categoria;
	}

	public BigDecimal getPreco() {
		return preco;
	}

	public void setPreco(BigDecimal preco) {
		this.preco = preco;
	}

	public boolean isAtivo() {
		return ativo;
	}

	public void setAtivo(boolean ativo) {
		this.ativo = ativo;
	}

	public Map<String, Object> getAtributos() {
		return atributos;
	}

	public void setAtributos(Map<String, Object> atributos) {
		this.atributos = atributos;
	}

	public Estoque getEstoque() {
		return estoque;
	}

	public void setEstoque(Estoque estoque) {
		this.estoque = estoque;
	}

}
