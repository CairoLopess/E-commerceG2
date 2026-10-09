package br.com.ecommerce.usuarios.entity;

import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "enderecos")
public class Endereco {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	// FK com ON DELETE CASCADE: apagar o usuário apaga os endereços dele
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "usuario_id", nullable = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	private Usuario usuario;

	private String apelido;

	@Column(nullable = false)
	private String cep;

	@Column(nullable = false)
	private String logradouro;

	private String numero;

	private String complemento;

	@Column(nullable = false)
	private String cidade;

	@Column(nullable = false, length = 2)
	private String uf;

	@Column(nullable = false)
	private boolean principal;

	protected Endereco() {
	}

	public Endereco(Usuario usuario, String apelido, String cep, String logradouro, String numero,
			String complemento, String cidade, String uf, boolean principal) {
		this.usuario = usuario;
		this.apelido = apelido;
		this.cep = cep;
		this.logradouro = logradouro;
		this.numero = numero;
		this.complemento = complemento;
		this.cidade = cidade;
		this.uf = uf;
		this.principal = principal;
	}

	public UUID getId() {
		return id;
	}

	public Usuario getUsuario() {
		return usuario;
	}

	public String getApelido() {
		return apelido;
	}

	public String getCep() {
		return cep;
	}

	public String getLogradouro() {
		return logradouro;
	}

	public String getNumero() {
		return numero;
	}

	public String getComplemento() {
		return complemento;
	}

	public String getCidade() {
		return cidade;
	}

	public String getUf() {
		return uf;
	}

	public boolean isPrincipal() {
		return principal;
	}

	public void setPrincipal(boolean principal) {
		this.principal = principal;
	}

}
