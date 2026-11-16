# ADR-016 — Idempotência: reservas por pedido e filtro de estado

**Contexto.** O Kafka entrega pelo menos uma vez: o offset só é confirmado depois que o listener
termina, então uma falha no meio faz o mesmo evento chegar de novo.

**Decisão.** No **catálogo**, a coleção `reservas` tem índice único em `pedidoId`: a reserva é
gravada antes de tirar qualquer estoque, e o segundo `pedido.criado` do mesmo pedido dá chave
duplicada e não reserva de novo; se a reserva já tem resultado, ele é republicado (cobre o caso de
a publicação ter falhado). Cada item sai do estoque com um update só, com
`estoque.disponivel >= quantidade` no filtro (`$inc` em `disponivel` e `reservado`): dois pedidos
ao mesmo tempo não deixam o estoque negativo. Faltou um item, os já reservados são devolvidos
(tudo ou nada). No **pedidos**, o status só muda a partir do estado esperado, num `UPDATE`
condicional; o cancelamento pelo cliente usa o mesmo `UPDATE`.

**Consequências.** Reservado não é vendido: o pagamento confirma a venda ou devolve ao disponível
(ADR-018). O `PATCH /produtos/{id}/estoque` continua definindo o disponível e não mexe no
reservado.
