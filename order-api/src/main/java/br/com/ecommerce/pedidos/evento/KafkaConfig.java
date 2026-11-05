package br.com.ecommerce.pedidos.evento;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Tópicos e tratamento de erro. Os tópicos são criados por quem sobe primeiro: cada serviço
 * declara os que usa, e o Spring (KafkaAdmin) só cria os que ainda não existem.
 */
@Configuration
public class KafkaConfig {

	public static final String TOPICO_PEDIDOS = "pedidos";
	public static final String TOPICO_ESTOQUE = "estoque";

	// 3 partições: até 3 réplicas de um serviço dividem o trabalho. Réplicas: o padrão do broker
	// (1 na docker-compose, que só tem um broker).
	@Bean
	NewTopic topicoPedidos() {
		return TopicBuilder.name(TOPICO_PEDIDOS).partitions(3).build();
	}

	@Bean
	NewTopic topicoEstoque() {
		return TopicBuilder.name(TOPICO_ESTOQUE).partitions(3).build();
	}

	/**
	 * Mensagem venenosa: um evento com defeito daria erro para sempre e travaria a partição.
	 * Tenta mais 2 vezes, 1 s entre elas, e segue para a próxima (o erro fica no log).
	 * O certo seria mandar para um tópico de erros (DLQ): limitação conhecida, ver README.
	 */
	@Bean
	DefaultErrorHandler errorHandler() {
		return new DefaultErrorHandler(new FixedBackOff(1000L, 2));
	}

}
