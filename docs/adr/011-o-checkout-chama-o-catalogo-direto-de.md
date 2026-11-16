# ADR-011 — O checkout chama o catálogo direto, de forma síncrona, com timeout de 3 s

**Contexto.** Na compra, o pedidos precisa do preço de agora, e quem sabe disso é o catálogo.
(Até a aula 2, também do estoque; a partir da aula 3, quem decide o estoque é a reserva por
evento, ADR-014.)

**Decisão.** `GET http://catalogo:3000/produtos?ids=a,b,c`: uma chamada para todos os produtos do
carrinho, pela rede interna, sem passar pelo gateway, repassando o `X-Request-Id`. Timeout de
conexão e de leitura de 3 s. Fora do ar, lento ou com erro → **503** no checkout. O carrinho
degrada em vez de falhar: com o catálogo fora, `GET /carrinho` devolve produto e quantidade, sem
preço, com um `aviso`. A lista de pedidos não depende do catálogo.

**Consequências.** Acoplamento temporal: para vender, o catálogo precisa estar no ar naquele
instante. `docker compose stop catalogo` derruba o checkout, mas não o resto do pedidos. O
`pedidos` não tem `depends_on` no catálogo de propósito.
