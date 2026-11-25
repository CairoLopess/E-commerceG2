# ADR-006 — `PATCH /produtos/{id}/estoque` define o valor disponível

**Decisão.** O corpo `{ "disponivel": 25 }` **define** o estoque disponível (não soma ao atual).
Valor negativo → 400. O campo `reservado` não é alterado por esta rota: quem mexe nele é a reserva
por evento (ADR-016).

**Consequências.** A operação é idempotente (repetir a mesma requisição dá o mesmo resultado).
Ajustes relativos (+/-) ficam para quando houver reserva de estoque.
