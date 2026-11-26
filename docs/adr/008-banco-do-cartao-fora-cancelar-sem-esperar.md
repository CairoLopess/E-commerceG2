# ADR-008 — Banco do cartão fora: cancelar o pedido, sem esperar

**Contexto.** Quando o banco do cartão não responde (timeout, 503 ou circuito aberto), o pagamentos
não sabe quando ele vai voltar. Há dois caminhos: cancelar o pedido logo ou deixá-lo esperando o
banco voltar.

**Decisão.** Cancelar. Esgotadas as 3 tentativas, ou com o circuito aberto, o pagamento fica `ERRO`
(com as tentativas e o motivo) e o pagamentos publica `pagamento.recusado` com o motivo "banco
indisponível (...)". A saga compensa como numa recusa do cartão: o pedido vira `CANCELADO` com o
motivo, o catálogo devolve o estoque e o cliente recebe o e-mail. Se alguma tentativa deu timeout, o
pagamentos ainda pede o estorno pela referência, porque timeout não quer dizer que o banco não
cobrou (ADR-007).

Esperar seria: pagamento `PENDENTE`, pedido em `AGUARDANDO_PAGAMENTO`, estoque reservado e alguém
tentando de novo, seja pausando o consumo com o circuito aberto (o container do `@KafkaListener` tem
`pause()`) e deixando os eventos no Kafka, seja uma rotina que cobra os `PENDENTE` de tempos em
tempos. Sempre com prazo: passadas, por exemplo, 2 horas, recusa e a saga compensa.

**Consequências.** É o caminho mais simples: o cliente tem resposta em segundos e o estoque não fica
preso. O preço é que banco fora vira venda perdida, mesmo que ele volte um minuto depois, e o
cliente precisa comprar de novo. Esperar não perderia a venda, mas prenderia estoque, deixaria o
cliente sem resposta e daria mais trabalho. É decisão de negócio, e está nas limitações conhecidas.
