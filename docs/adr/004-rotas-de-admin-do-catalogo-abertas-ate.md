# ADR-004 — Rotas de admin do catálogo abertas até a aula 2

> **Resolvido pelo ADR-008 (aula 2).** As rotas de admin agora exigem token com perfil admin no
> gateway.

**Contexto.** O catálogo não deveria saber validar o token emitido por outro serviço.

**Decisão.** `POST/PUT/PATCH/DELETE` de produtos e `POST /categorias` ficam sem autenticação nesta
entrega, como pede o enunciado.

**Consequências.** Qualquer um pode alterar o catálogo enquanto não houver gateway. Aceitável só
em ambiente de aula. Resolvido na aula 2.
