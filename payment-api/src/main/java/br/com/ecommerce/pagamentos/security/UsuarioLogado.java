package br.com.ecommerce.pagamentos.security;

/**
 * Cabeçalhos com a identidade de quem chamou. Quem escreve é o gateway, a partir do token
 * já validado; o que o cliente mandar com esses nomes é apagado antes (ver kong/kong.yml).
 * O serviço confia neles porque só o gateway o alcança: nenhuma porta é publicada.
 */
public final class UsuarioLogado {

	// O "sub" do token: o id do usuário
	public static final String ID = "X-User-Id";

	// O "perfil" do token: cliente ou admin. O Kong deixa qualquer perfil logado passar em
	// /pagamentos; quem confere dono e admin é o serviço.
	public static final String PERFIL = "X-User-Role";

	public static final String ADMIN = "admin";

	private UsuarioLogado() {
	}

}
