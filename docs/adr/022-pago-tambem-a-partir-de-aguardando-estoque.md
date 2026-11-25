# ADR-022 — `PAGO` também a partir de `AGUARDANDO_ESTOQUE`

**Contexto.** O slide pede `PAGO` só a partir de `AGUARDANDO_PAGAMENTO`. Mas o pedidos e o
pagamentos reagem ao **mesmo** `estoque.reservado`, e o `pagamento.aprovado` vem por outro tópico,
sem ordem garantida em relação a ele. Se o pagamento terminar antes de o pedidos tratar o
`estoque.reservado` (pedidos reiniciando, com lag), o aprovado seria ignorado e o pedido ficaria em
`AGUARDANDO_PAGAMENTO` para sempre, pago.

**Decisão.** `pagamento.aprovado` leva a `PAGO` a partir de `AGUARDANDO_ESTOQUE` ou
`AGUARDANDO_PAGAMENTO` (o `pagamento.recusado` já aceitava os dois, como no slide). Um aprovado só
existe se o estoque foi reservado, então o salto é seguro; o `estoque.reservado` que chegar depois é
ignorado pelo filtro de estado. O que importa continua valendo: `CANCELADO` nunca vira `PAGO`.

**Consequências.** Nenhum pedido pago fica preso por causa de uma corrida entre tópicos.
