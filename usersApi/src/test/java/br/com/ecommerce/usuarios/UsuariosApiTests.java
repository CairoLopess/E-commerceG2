package br.com.ecommerce.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import br.com.ecommerce.usuarios.repository.EnderecoRepository;
import br.com.ecommerce.usuarios.repository.UsuarioRepository;
import br.com.ecommerce.usuarios.seed.DataSeeder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuariosApiTests {

	@Autowired
	MockMvc mvc;

	@Autowired
	DataSeeder seeder;

	@Autowired
	UsuarioRepository usuarioRepository;

	@Autowired
	EnderecoRepository enderecoRepository;

	@Test
	void seedEhIdempotente() throws Exception {
		long usuarios = usuarioRepository.count();
		long enderecos = enderecoRepository.count();

		seeder.run(null);

		assertThat(usuarioRepository.count()).isEqualTo(usuarios);
		assertThat(enderecoRepository.count()).isEqualTo(enderecos);
	}

	@Test
	void loginDevolveTokenEUsuarioSemSenha() throws Exception {
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"cliente@loja.com\",\"senha\":\"cliente123\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.token").isString())
			.andExpect(jsonPath("$.usuario.email").value("cliente@loja.com"))
			.andExpect(jsonPath("$.usuario.perfil").value("cliente"))
			.andExpect(jsonPath("$.usuario.senhaHash").doesNotExist());
	}

	@Test
	void loginComSenhaErradaDevolve401() throws Exception {
		mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"cliente@loja.com\",\"senha\":\"errada\"}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.erro").value("E-mail ou senha inválidos."));
	}

	@Test
	void cadastroCria201EEmailRepetidoDevolve409() throws Exception {
		String email = "ana-" + UUID.randomUUID() + "@loja.com";
		String corpo = "{\"nome\":\"Ana\",\"email\":\"" + email + "\",\"senha\":\"segredo123\"}";

		mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").isString())
			.andExpect(jsonPath("$.perfil").value("cliente"))
			.andExpect(jsonPath("$.senha").doesNotExist())
			.andExpect(jsonPath("$.senhaHash").doesNotExist());

		mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content(corpo))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.erro").value("Este e-mail já está cadastrado."));
	}

	@Test
	void cadastroInvalidoDevolve400ComDetalhes() throws Exception {
		mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"\",\"email\":\"nao-e-email\",\"senha\":\"123\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Dados inválidos."))
			.andExpect(jsonPath("$.detalhes", hasSize(3)))
			.andExpect(jsonPath("$.detalhes", hasItem("email inválido")));
	}

	@Test
	void jsonMalformadoDevolve400NoFormatoPadrao() throws Exception {
		mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON).content("{nome"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.erro").value("Requisição inválida."));
	}

	@Test
	void meSemTokenDevolve401() throws Exception {
		mvc.perform(get("/usuarios/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.erro").value("Token ausente, inválido ou vencido."));
	}

	@Test
	void meComTokenInvalidoDevolve401() throws Exception {
		mvc.perform(get("/usuarios/me").header("Authorization", "Bearer token.falso.aqui"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.erro").value("Token ausente, inválido ou vencido."));
	}

	@Test
	void meDevolveDadosEPutAtualiza() throws Exception {
		String email = "bia-" + UUID.randomUUID() + "@loja.com";
		String token = cadastrarELogar(email, "segredo123");

		mvc.perform(get("/usuarios/me").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(email));

		String novoEmail = "beatriz-" + UUID.randomUUID() + "@loja.com";
		mvc.perform(put("/usuarios/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Beatriz\",\"email\":\"" + novoEmail + "\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.nome").value("Beatriz"))
			.andExpect(jsonPath("$.email").value(novoEmail));

		// Trocar para um e-mail que já é de outra pessoa
		mvc.perform(put("/usuarios/me").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Beatriz\",\"email\":\"admin@loja.com\"}"))
			.andExpect(status().isConflict());
	}

	@Test
	void listarUsuariosSoParaAdmin() throws Exception {
		String tokenCliente = logar("cliente@loja.com", "cliente123");
		mvc.perform(get("/usuarios").header("Authorization", "Bearer " + tokenCliente))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.erro").value("Você não tem permissão para acessar este recurso."));

		String tokenAdmin = logar("admin@loja.com", "admin123");
		mvc.perform(get("/usuarios").header("Authorization", "Bearer " + tokenAdmin))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[*].email", hasItem("admin@loja.com")))
			.andExpect(jsonPath("$[0].senhaHash").doesNotExist());
	}

	@Test
	void enderecosCrudEIsolamentoEntreUsuarios() throws Exception {
		String token = cadastrarELogar("carla-" + UUID.randomUUID() + "@loja.com", "segredo123");
		String endereco = """
				{"apelido":"Casa","cep":"90010-000","logradouro":"Rua A","numero":"10",
				 "cidade":"Porto Alegre","uf":"rs","principal":true}""";

		String criado = mvc.perform(post("/usuarios/me/enderecos").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content(endereco))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.uf").value("RS"))
			.andExpect(jsonPath("$.principal").value(true))
			.andReturn().getResponse().getContentAsString();
		String enderecoId = JsonPath.read(criado, "$.id");

		mvc.perform(get("/usuarios/me/enderecos").header("Authorization", "Bearer " + token))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$", hasSize(1)));

		// Outro usuário não enxerga nem apaga o endereço: 404
		String tokenCliente = logar("cliente@loja.com", "cliente123");
		mvc.perform(delete("/usuarios/me/enderecos/" + enderecoId).header("Authorization", "Bearer " + tokenCliente))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.erro").value("Endereço não encontrado."));

		mvc.perform(delete("/usuarios/me/enderecos/" + enderecoId).header("Authorization", "Bearer " + token))
			.andExpect(status().isNoContent());

		mvc.perform(delete("/usuarios/me/enderecos/" + enderecoId).header("Authorization", "Bearer " + token))
			.andExpect(status().isNotFound());
	}

	@Test
	void enderecoInvalidoDevolve400() throws Exception {
		String token = logar("cliente@loja.com", "cliente123");
		mvc.perform(post("/usuarios/me/enderecos").header("Authorization", "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON).content("{\"cep\":\"abc\",\"uf\":\"RSS\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detalhes", hasItem("cep deve ter o formato 00000-000")))
			.andExpect(jsonPath("$.detalhes", hasItem("uf deve ter 2 letras")));
	}

	@Test
	void healthDevolveOk() throws Exception {
		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ok"))
			.andExpect(jsonPath("$.banco").value("ok"));
	}

	private String cadastrarELogar(String email, String senha) throws Exception {
		mvc.perform(post("/usuarios").contentType(MediaType.APPLICATION_JSON)
				.content("{\"nome\":\"Teste\",\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
			.andExpect(status().isCreated());
		return logar(email, senha);
	}

	private String logar(String email, String senha) throws Exception {
		String resposta = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"" + email + "\",\"senha\":\"" + senha + "\"}"))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();
		return JsonPath.read(resposta, "$.token");
	}

}
