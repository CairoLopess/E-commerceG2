# ADR-007 — Preço como `BigDecimal` / `Decimal128`

**Decisão.** O preço é `BigDecimal` no Java e `Decimal128` no Mongo, com no máximo 2 casas
decimais e sempre maior que zero.

**Consequências.** Sem erros de arredondamento de `double` em valores monetários, o que importa
quando os pedidos começarem a somar preços.
