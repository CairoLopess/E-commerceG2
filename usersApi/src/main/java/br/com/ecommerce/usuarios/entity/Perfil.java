package br.com.ecommerce.usuarios.entity;

public enum Perfil {

	CLIENTE("cliente"),
	ADMIN("admin");

	// Valor exposto na API e no token (o banco guarda o nome do enum: CLIENTE / ADMIN)
	private final String valor;

	Perfil(String valor) {
		this.valor = valor;
	}

	public String valor() {
		return valor;
	}

}
