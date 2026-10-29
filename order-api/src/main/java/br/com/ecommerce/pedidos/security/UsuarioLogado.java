package br.com.ecommerce.pedidos.security;

/**
 * Cabeçalhos com a identidade de quem chamou. Quem escreve é o gateway, a partir do token
 * já validado; o que o cliente mandar com esses nomes é apagado antes (ver kong/kong.yml).
 * O serviço confia neles porque só o gateway o alcança: nenhuma porta é publicada.
 */
public final class UsuarioLogado {

	// O "sub" do token: o id do usuário
	public static final String ID = "X-User-Id";

	public static final String EMAIL = "X-User-Email";

	// Gerado pelo gateway (correlation-id); repassado ao catálogo para rastrear a requisição
	public static final String REQUEST_ID = "X-Request-Id";

	private UsuarioLogado() {
	}

}
