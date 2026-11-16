# ADR-017 — Spring for Apache Kafka no lugar do kafkajs dos slides

**Contexto.** Os exemplos da aula usam Node (kafkajs, nodemailer); os serviços deste projeto são
Spring Boot.

**Decisão.** `spring-boot-starter-kafka` com `KafkaTemplate<String, String>` e `@KafkaListener`: o
valor da mensagem é texto (JSON), lido e escrito pela classe `Eventos` de cada serviço, que faz o
papel do `kafka.js` dos slides. Tópicos declarados como `NewTopic` (o Spring faz o `listTopics` +
`createTopics`). `auto-offset-reset=earliest` equivale ao `fromBeginning` (só vale para grupo novo).
O Spring confirma o offset depois que o listener termina, como o kafkajs. Erro no listener: mais 2
tentativas com 1 s entre elas e depois segue (sem DLQ). Os e-mails saem com
`spring-boot-starter-mail` para o Mailpit. No compose, `stop_grace_period: 10s` dá tempo de o
consumer sair do grupo ao parar.

**Consequências.** Mesma semântica dos slides (pelo menos uma vez, chave = pedido, um grupo por
serviço), com o ecossistema que o projeto já usa. O notificacoes é um Spring Boot sem web
(`web-application-type=none`): fica no ar enquanto o listener roda.
