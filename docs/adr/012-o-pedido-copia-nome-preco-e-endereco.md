# ADR-012 — O pedido copia nome, preço e endereço; sem FK para outros serviços

**Decisão.** Três tabelas no Postgres do pedidos: `carrinho_itens` (chave `usuario_id` +
`produto_id`, `CHECK quantidade > 0`), `pedidos` (status com `CHECK` nos 4 estados,
`endereco_entrega` em JSONB) e `itens_pedido` (FK para `pedidos`, que está no mesmo banco). Sem FK
para usuários e produtos, que moram em outros bancos: quem garante que existem é o código,
perguntando ao serviço dono. No checkout, nome e preço de cada item são copiados do catálogo, e o
endereço vem no corpo da requisição e é gravado dentro do pedido. Do cartão, só os 4 últimos
dígitos.

**Consequências.** O pedido de hoje não muda se o produto mudar de preço amanhã, ou se o cliente
mudar de endereço: é histórico, não duplicação. O checkout não precisa chamar o usuarios. Como no
usuarios, o esquema é gerado pelo Hibernate (`ddl-auto=update`, ver ADR-002).
