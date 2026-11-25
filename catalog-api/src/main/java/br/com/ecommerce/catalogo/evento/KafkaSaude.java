package br.com.ecommerce.catalogo.evento;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/**
 * Pergunta ao Kafka se ele está de pé (describeCluster), para o /health. Um teste de verdade e
 * barato: o /health é chamado a cada 10 s. O cliente de admin é criado uma vez só e reaproveitado.
 * A mesma classe existe em cada serviço que usa Kafka.
 */
@Component
public class KafkaSaude implements DisposableBean {

	private final KafkaAdmin kafkaAdmin;
	private volatile AdminClient admin;

	public KafkaSaude(KafkaAdmin kafkaAdmin) {
		this.kafkaAdmin = kafkaAdmin;
	}

	// true se algum broker respondeu em até 2 s
	public boolean noAr() {
		try {
			return !admin().describeCluster(new DescribeClusterOptions().timeoutMs(2000))
					.nodes().get(2500, TimeUnit.MILLISECONDS).isEmpty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		} catch (Exception e) {
			return false;
		}
	}

	private AdminClient admin() {
		if (admin == null) {
			synchronized (this) {
				if (admin == null) {
					admin = AdminClient.create(kafkaAdmin.getConfigurationProperties());
				}
			}
		}
		return admin;
	}

	@Override
	public void destroy() {
		if (admin != null) {
			admin.close(Duration.ofSeconds(1));
		}
	}

}
