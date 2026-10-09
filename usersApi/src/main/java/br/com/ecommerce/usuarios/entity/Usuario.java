package br.com.ecommerce.usuarios.entity;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false)
	private String nome;

	@Column(nullable = false, unique = true)
	private String email;

	// bcrypt, nunca a senha
	@Column(nullable = false)
	private String senhaHash;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Perfil perfil = Perfil.CLIENTE;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant criadoEm;

	protected Usuario() {
	}

	public Usuario(String nome, String email, String senhaHash, Perfil perfil) {
		this.nome = nome;
		this.email = email;
		this.senhaHash = senhaHash;
		this.perfil = perfil;
	}

	public UUID getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public void setNome(String nome) {
		this.nome = nome;
	}

	public String getEmail() {
		return email;
	}

	public void setEmail(String email) {
		this.email = email;
	}

	public String getSenhaHash() {
		return senhaHash;
	}

	public void setSenhaHash(String senhaHash) {
		this.senhaHash = senhaHash;
	}

	public Perfil getPerfil() {
		return perfil;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

}
