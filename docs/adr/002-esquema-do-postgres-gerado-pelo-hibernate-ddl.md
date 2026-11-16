# ADR-002 — Esquema do Postgres gerado pelo Hibernate (`ddl-auto=update`), sem migrations

**Contexto.** A estrutura sugerida nos slides tem uma pasta `migrations/`. Em Java, as opções eram
Flyway/Liquibase, `schema.sql` ou deixar o Hibernate gerar as tabelas a partir das entities.

**Decisão.** `spring.jpa.hibernate.ddl-auto=update`. As regras do SQL do enunciado estão nas
anotações das entities: `unique = true` no e-mail, `nullable = false`, `@OnDelete(CASCADE)` na FK,
`length = 2` na UF, e o perfil como enum (o Hibernate gera um `CHECK` com os valores válidos).

**Consequências.** Menos arquivos para manter nesta fase. Porém o `update` **só adiciona**: nunca
remove nem renomeia colunas. Se um campo for renomeado, a coluna antiga fica no banco; nesse caso,
`docker compose down -v`. Diferenças em relação ao SQL do slide: o UUID e o `criado_em` são gerados
pela aplicação (não por `DEFAULT` no banco), e o perfil é gravado como `CLIENTE`/`ADMIN` (a API
expõe em minúsculo). Se o esquema começar a mudar com frequência, migrar para Flyway.
