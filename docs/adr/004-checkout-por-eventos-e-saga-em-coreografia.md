# ADR-004 — Checkout por eventos (Kafka) e saga em coreografia

**Contexto.** Com o checkout em corrente (pedidos → catálogo → pagamento → e-mail), cada serviço a
mais multiplica a chance de falhar e soma latência, e o pedidos precisa conhecer todos. Além disso,
a compra grava em três bancos de três serviços, e não há transação que desfaça os três. O commit em
duas fases (2PC) exigiria todos no ar ao mesmo tempo e travaria registros, e o Kafka, o Mongo e o
banco do cartão não participam dele.

**Decisão.** Kafka 3.8 em modo KRaft (um container, broker e controller, sem ZooKeeper). O
checkout publica `pedido.criado` e responde 202; o resto acontece por eventos (seção
[Eventos](../../README.md#eventos)). Coreografia: o pedidos não sabe que o catálogo reserva, só reage ao
resultado; o notificacoes só escuta. O checkout ainda consulta o catálogo de forma síncrona, mas só
para o preço, que precisa ser o de agora. Quem decide o estoque é a reserva, então o "sem estoque"
não é um 409 no checkout: é o pedido `CANCELADO` com o motivo.

Desfazer o que já foi feito é uma **saga**, também em coreografia: cada passo é uma transação local
e cada serviço conhece só a própria compensação (seção
[A saga da compra](../../README.md#a-saga-da-compra)). Nada de orquestrador: o fluxo tem poucos
passos. As compensações são idempotentes por filtro de estado: a reserva tem os status
`CONFIRMADO` e `LIBERADO`, mudados com `findAndModify` e o status de origem no filtro, e quem recebe
a reserva de volta é o único que mexe no estoque. O cancelamento depois de confirmado (o cliente
cancelou enquanto o banco aprovava) também devolve o estoque, porque o pagamentos estorna essa
cobrança. A ordem dos passos segue o custo de desfazer: reservar (barato) antes de cobrar (estorno
custa taxa); e-mail de confirmação só depois do pagamento aprovado.

**Consequências.** Consistência eventual e sem isolamento: por alguns instantes o pedido existe e o
estoque ainda não foi reservado, o mesmo `GET /pedidos/{id}` responde diferente em instantes
diferentes, e outro cliente pode ver "esgotado" por causa de uma reserva que vai ser devolvida. O
fluxo inteiro só aparece somando todos os serviços (por isso o `correlationId` em todos os eventos e
logs). Um serviço caído não derruba a compra: os eventos esperam no tópico. Uma compensação que
falha de vez fica só no log (limitação conhecida).
