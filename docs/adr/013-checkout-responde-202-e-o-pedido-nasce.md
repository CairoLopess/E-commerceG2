# ADR-013 — Checkout responde 202 e o pedido nasce em `AGUARDANDO_ESTOQUE`

> **Atualizado pelo ADR-014 (aula 3).** O checkout não confere mais o estoque: o catálogo reserva
> ao receber `pedido.criado`, e o pedido sai de `AGUARDANDO_ESTOQUE` sozinho.

**Decisão.** O checkout confere se cada produto está ativo e tem estoque (409 se não), mas **não
reserva** o estoque: a reserva entra na aula 3, por evento (Kafka). O pedido é gravado junto com a
limpeza do carrinho, numa transação só, e a resposta é **202 Accepted** com `Location`.

**Consequências.** Entre o checkout e a reserva, dois clientes podem comprar a última unidade. Na
aula 3, o catálogo reserva ao receber o evento `pedido.criado` e o pedido sai de
`AGUARDANDO_ESTOQUE` sozinho. O cliente pode cancelar enquanto o pedido não foi pago (409 depois).
