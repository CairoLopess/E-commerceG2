# ADR-026 — Sobe do zero com um comando: valores padrão no compose e memória limitada

**Contexto.** O critério da entrega é um clone novo subir com `docker compose up -d --build` e o
`.http` vender, sem passo manual. O compose exigia um `.env` (o `JWT_SECRET` era obrigatório), que
não vai para o Git. E, numa máquina com 2 GB para o WSL, a pilha inteira travava a VM.

**Decisão.** Toda variável do compose tem valor padrão, inclusive o `JWT_SECRET` (um segredo de
desenvolvimento com mais de 32 caracteres, o mesmo no usuarios e no Kong, por uma âncora YAML). O
`.env.example` virou opcional: serve para trocar valores ou rodar um serviço fora do Docker.
Tabelas (Hibernate no usuarios e no pedidos, `schema.sql` no pagamentos), índices, seeds e tópicos
são criados na subida e são idempotentes. Cada JVM tem heap limitado (`-Xmx128m`, GC serial, JIT
de nível 1; trocável por `JAVA_OPTS_SERVICOS`), o Kafka 256 MB (`KAFKA_HEAP_OPTS`), o Kafka UI
128 MB e o cache do Mongo 256 MB.

**Consequências.** Qualquer um sobe a loja sem configurar nada, e por isso mesmo o segredo padrão é
público: num ambiente de verdade, `JWT_SECRET` precisa ser definido. Com heap pequeno, um pico de
carga pode dar `OutOfMemoryError` (no teste de carga da próxima aula, os limites sobem). As tabelas
continuam sem migrations versionadas (ADR-002).
