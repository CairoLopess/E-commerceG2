# ADR-014 — Checkout por eventos (Kafka), em coreografia

**Contexto.** Com o checkout em corrente (pedidos → catálogo → pagamento → e-mail), cada serviço a
mais multiplica a chance de falhar e soma latência, e o pedidos precisa conhecer todos.

**Decisão.** Kafka 3.8 em modo KRaft (um container, broker e controller, sem ZooKeeper). O
checkout publica `pedido.criado` e responde 202; o resto acontece por eventos (seção
[Eventos](../../README.md#eventos)). Coreografia: o pedidos não sabe que o catálogo reserva, só reage ao
resultado; o notificacoes só escuta. A consulta síncrona ao catálogo continua no checkout, mas só
para o preço (ADR-011): o preço precisa ser o de agora. O checkout não confere mais o estoque
(quem decide é a reserva), então o "sem estoque" deixou de ser 409 e virou `CANCELADO` com motivo.

**Consequências.** Consistência eventual: por alguns instantes, o pedido existe e o estoque ainda
não foi reservado; o mesmo `GET /pedidos/{id}` responde diferente em instantes diferentes. O fluxo
inteiro só aparece somando todos os serviços (por isso o `correlationId` em todos os eventos e
logs). Um serviço caído não derruba a compra: os eventos esperam no tópico. Desfazer passos quando
algo falha no meio é a saga (ADR-018, aula 4).
