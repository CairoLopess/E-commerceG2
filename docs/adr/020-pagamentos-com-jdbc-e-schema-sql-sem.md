# ADR-020 — Pagamentos com JDBC e `schema.sql`, sem JPA

**Decisão.** A idempotência está no SQL: `INSERT ... ON CONFLICT DO NOTHING` com `pedido_id UNIQUE`,
e toda mudança de status com o status de origem no `WHERE` (`concluir` só de `PENDENTE`,
`marcarEstornado` só de `APROVADO` ou `CANCELADO`). Com JPA, isso viraria consultas nativas de
qualquer jeito, então o serviço usa `JdbcTemplate`, e a tabela vem de um
[`schema.sql`](../../payment-api/src/main/resources/schema.sql) com `CREATE TABLE IF NOT EXISTS`, igual à
do slide (o UUID é gerado pela aplicação, como no ADR-002). O mesmo SQL roda no H2 dos testes, em
modo PostgreSQL.

**Consequências.** O SQL fica à vista e é o mesmo do slide. Mudança de esquema exige mexer no
`schema.sql` à mão (ou `docker compose down -v`); se crescer, Flyway.
