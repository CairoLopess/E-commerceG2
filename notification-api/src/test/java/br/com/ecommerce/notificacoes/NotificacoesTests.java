package br.com.ecommerce.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import br.com.ecommerce.notificacoes.evento.NotificacoesListener;

/**
 * O teste faz o papel do Kafka (entrega a mensagem ao listener) e o SMTP é simulado: olha o
 * e-mail que sairia para o Mailpit.
 */
@SpringBootTest
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class NotificacoesTests {

	@MockitoBean
	JavaMailSender smtp;

	@Autowired
	NotificacoesListener listener;

	@Test
	void pedidoCriadoMandaRecebemosOSeuPedido(CapturedOutput saida) {
		listener.receber(mensagem("pedido.criado", """
				{"usuarioId":"ana","clienteEmail":"cliente@loja.com","valorTotal":2239.70,"formaPagamento":"pix",
				 "itens":[{"produtoId":"1","nome":"Effective Java","precoUnitario":119.90,"quantidade":2},
				          {"produtoId":"2","nome":"Smartphone X","precoUnitario":1999.90,"quantidade":1}]}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getTo()).containsExactly("cliente@loja.com");
		assertThat(email.getFrom()).isEqualTo("loja@ecommerce.local");
		assertThat(email.getSubject()).isEqualTo("Recebemos o seu pedido");
		assertThat(email.getText())
			.contains("pedido 7d0c1b2e-0000-0000-0000-000000000001")
			.contains("- 2x Effective Java (R$ 119,90)")
			.contains("- 1x Smartphone X (R$ 1.999,90)")
			.contains("Total: R$ 2.239,70")
			.contains("Pagamento: pix");
		// O correlationId do evento vai para o log
		assertThat(saida.getOut()).contains("[corr-teste]").contains("enviado para cliente@loja.com");
	}

	@Test
	void estoqueInsuficienteMandaOMotivo() {
		listener.receber(mensagem("estoque.insuficiente", """
				{"motivo":"Estoque insuficiente para Smartphone X Pro: pedido 6, disponível 5.",
				 "usuarioId":"ana","clienteEmail":"cliente@loja.com"}"""));

		SimpleMailMessage email = enviado();
		assertThat(email.getTo()).containsExactly("cliente@loja.com");
		assertThat(email.getSubject()).isEqualTo("Seu pedido foi cancelado: sem estoque");
		assertThat(email.getText()).contains("Estoque insuficiente para Smartphone X Pro: pedido 6, disponível 5.");
	}

	@Test
	void eventosQueNaoInteressamNaoMandamEmail() {
		// estoque.reservado: o cliente fica sabendo quando o pagamento for aprovado (próxima aula)
		listener.receber(mensagem("estoque.reservado", "{\"clienteEmail\":\"cliente@loja.com\"}"));
		// Sem e-mail no evento: não tem para quem mandar
		listener.receber(mensagem("estoque.insuficiente", "{\"motivo\":\"x\"}"));

		verify(smtp, never()).send(any(SimpleMailMessage.class));
	}

	private SimpleMailMessage enviado() {
		ArgumentCaptor<SimpleMailMessage> email = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(smtp).send(email.capture());
		return email.getValue();
	}

	private static String mensagem(String tipo, String dados) {
		return """
				{"id":"e1","tipo":"%s","pedidoId":"7d0c1b2e-0000-0000-0000-000000000001",
				 "ocorridoEm":"2026-11-05T12:00:00Z","correlationId":"corr-teste","dados":%s}""".formatted(tipo, dados);
	}

}
