package br.com.ecommerce.banco;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class BancoFalsoTests {

	@Autowired
	MockMvc mvc;

	@AfterEach
	void voltarAoNormal() throws Exception {
		config("{\"latenciaMs\":0,\"falhaPercent\":0}").andExpect(status().isOk());
	}

	@Test
	void cartaoComumOuPixAprova() throws Exception {
		cobrar(UUID.randomUUID().toString(), "4111")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.aprovado").value(true))
			.andExpect(jsonPath("$.codigo").value(org.hamcrest.Matchers.startsWith("TX-")));
		cobrar(UUID.randomUUID().toString(), null).andExpect(status().isCreated());
	}

	@Test
	void cartaoFinal0002Recusa402() throws Exception {
		cobrar(UUID.randomUUID().toString(), "0002")
			.andExpect(status().isPaymentRequired())
			.andExpect(jsonPath("$.aprovado").value(false))
			.andExpect(jsonPath("$.motivo").value("saldo insuficiente"));
	}

	@Test
	void mesmaReferenciaDevolveAMesmaCobranca() throws Exception {
		String referencia = UUID.randomUUID().toString();
		String primeira = cobrar(referencia, "4111").andReturn().getResponse().getContentAsString();

		cobrar(referencia, "4111")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.codigo").value(JsonPath.<String>read(primeira, "$.codigo")));
	}

	@Test
	void falhaPercent100DevolveSempre503() throws Exception {
		config("{\"falhaPercent\":100}")
			.andExpect(jsonPath("$.falhaPercent").value(100))
			.andExpect(jsonPath("$.latenciaMs").value(0));

		for (int i = 0; i < 5; i++) {
			cobrar(UUID.randomUUID().toString(), "4111").andExpect(status().isServiceUnavailable());
		}
		mvc.perform(get("/config")).andExpect(jsonPath("$.falhaPercent").value(100));
	}

	@Test
	void configInvalidaDevolve400() throws Exception {
		config("{\"falhaPercent\":101}").andExpect(status().isBadRequest());
		config("{\"latenciaMs\":-1}").andExpect(status().isBadRequest());
	}

	@Test
	void estornoPelaReferenciaEDepoisRepetido() throws Exception {
		String referencia = UUID.randomUUID().toString();
		String codigo = JsonPath.read(cobrar(referencia, "4111").andReturn().getResponse().getContentAsString(),
				"$.codigo");

		estornar("{\"referencia\":\"" + referencia + "\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.codigo").value(codigo));
		// Idempotente: estornar de novo não devolve duas vezes, responde igual
		estornar("{\"codigo\":\"" + codigo + "\",\"referencia\":\"" + referencia + "\"}")
			.andExpect(status().isCreated());
	}

	@Test
	void estornoSemCobrancaAprovadaDevolve404() throws Exception {
		estornar("{\"referencia\":\"nunca-cobrada\"}").andExpect(status().isNotFound());

		String recusada = UUID.randomUUID().toString();
		cobrar(recusada, "0002").andExpect(status().isPaymentRequired());
		estornar("{\"referencia\":\"" + recusada + "\"}").andExpect(status().isNotFound());
	}

	@Test
	void healthDevolveOk() throws Exception {
		mvc.perform(get("/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.servico").value("banco-falso"))
			.andExpect(jsonPath("$.status").value("ok"));
	}

	@Test
	void cobrancaSemReferenciaDevolve400() throws Exception {
		mvc.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON).content("{\"valor\":10}"))
			.andExpect(status().isBadRequest());
	}

	private ResultActions cobrar(String referencia, String cartaoFinal) throws Exception {
		String cartao = cartaoFinal == null ? "" : ",\"cartaoFinal\":\"" + cartaoFinal + "\"";
		return mvc.perform(post("/cobrancas").contentType(MediaType.APPLICATION_JSON)
				.content("{\"valor\":60.00,\"referencia\":\"" + referencia + "\"" + cartao + "}"));
	}

	private ResultActions estornar(String corpo) throws Exception {
		return mvc.perform(post("/estornos").contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

	private ResultActions config(String corpo) throws Exception {
		return mvc.perform(post("/config").contentType(MediaType.APPLICATION_JSON).content(corpo));
	}

}
