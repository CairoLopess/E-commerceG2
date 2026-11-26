# ADR-006 — Publicar depois do commit, sem Outbox

**Contexto.** O checkout grava o pedido no Postgres e publica o `pedido.criado` no Kafka. São dois
sistemas, sem uma transação que cubra os dois: um acontece antes do outro, e o segundo pode falhar.

**Decisão.** O `pedido.criado` é publicado **depois** do commit da transação que grava o pedido e
limpa o carrinho: nunca se anuncia um pedido que pode sumir num rollback. O envio espera a
confirmação do Kafka (até 10 s; o produtor desiste de esperar metadados em 5 s). Se falhar, o
checkout responde 202 mesmo assim (o pedido existe) e escreve um `ERROR` no log com o id.

O cancelamento faz o contrário: o `pedido.cancelado` é publicado **antes** do commit, na mesma
transação do `UPDATE`. Um pedido `CANCELADO` sem o evento deixaria o estoque preso e a cobrança
seguindo; com o Kafka fora, o cancelamento é desfeito e o cliente recebe 503 e tenta de novo.

**Consequências.** Escrita dupla: se o Kafka cair entre o commit e o publicar, o pedido fica parado
em `AGUARDANDO_ESTOQUE`. No cancelamento, se o commit falhar depois de o evento sair (raro), o
estoque volta e a cobrança é bloqueada, mas o pedido continua `AGUARDANDO_*`. A solução para os dois
casos (Outbox) fica como limitação conhecida.
