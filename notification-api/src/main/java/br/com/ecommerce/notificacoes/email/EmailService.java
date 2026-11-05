package br.com.ecommerce.notificacoes.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Manda o e-mail por SMTP. Na docker-compose, o servidor é o Mailpit: um SMTP de mentira que
 * recebe tudo e mostra numa caixa de entrada em localhost:8025. Nada sai para a internet.
 *
 * Se o SMTP estiver fora, a exceção sobe: o offset não é confirmado e o Kafka entrega de novo.
 */
@Service
public class EmailService {

	private static final Logger log = LoggerFactory.getLogger(EmailService.class);

	private final JavaMailSender smtp;
	private final String remetente;

	public EmailService(JavaMailSender smtp, @Value("${notificacoes.remetente}") String remetente) {
		this.smtp = smtp;
		this.remetente = remetente;
	}

	public void enviar(String para, String assunto, String texto) {
		if (para == null || para.isBlank()) {
			log.warn("Evento sem clienteEmail: e-mail \"{}\" não enviado", assunto);
			return;
		}
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(remetente);
		mensagem.setTo(para);
		mensagem.setSubject(assunto);
		mensagem.setText(texto);
		smtp.send(mensagem);
		log.info("E-mail \"{}\" enviado para {}", assunto, para);
	}

}
