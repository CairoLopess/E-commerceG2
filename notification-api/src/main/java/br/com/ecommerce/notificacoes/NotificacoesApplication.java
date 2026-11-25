package br.com.ecommerce.notificacoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Serviço só de escuta: ninguém chama, ninguém conhece. Sem banco, sem porta, sem rota no Kong.
 * Se cair, a loja continua vendendo; os eventos esperam no Kafka e os e-mails saem quando ele
 * voltar (o grupo continua do último offset confirmado).
 */
@SpringBootApplication
public class NotificacoesApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificacoesApplication.class, args);
	}

}
