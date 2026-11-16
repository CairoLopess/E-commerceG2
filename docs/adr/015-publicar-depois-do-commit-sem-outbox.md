# ADR-015 — Publicar depois do commit, sem Outbox

**Decisão.** O `pedido.criado` é publicado **depois** do commit da transação que grava o pedido e
limpa o carrinho: nunca se anuncia um pedido que pode sumir num rollback. O envio espera a
confirmação do Kafka (até 10 s; o produtor desiste de esperar metadados em 5 s). Se falhar, o
checkout responde 202 mesmo assim (o pedido existe) e escreve um `ERROR` no log com o id.

**Consequências.** Escrita dupla: se o Kafka cair entre o commit e o publicar, o pedido fica parado
em `AGUARDANDO_ESTOQUE`. A solução (Outbox) fica como limitação conhecida.
