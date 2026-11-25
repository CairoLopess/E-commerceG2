# ADR-021 — Cancelar publica o `pedido.cancelado` antes do commit

**Contexto.** No checkout, o evento sai depois do commit (ADR-015): melhor um pedido parado do que um
evento de um pedido que sumiu. No cancelamento, a conta é outra: um pedido `CANCELADO` sem o evento
deixaria o estoque preso e a cobrança seguindo, e o cliente pagaria por um pedido cancelado.

**Decisão.** O `UPDATE` condicional e o `publicar` (que espera o Kafka confirmar) ficam na mesma
transação. Kafka fora: rollback e **503**, o pedido continua como estava e o cliente tenta de novo.

**Consequências.** Se o commit falhar depois de o evento sair (raro), o catálogo devolve o estoque e
o pagamentos bloqueia a cobrança, mas o pedido continua `AGUARDANDO_*`. Resolver de vez é o Outbox.
