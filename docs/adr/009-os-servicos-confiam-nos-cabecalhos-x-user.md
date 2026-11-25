# ADR-009 — Os serviços confiam nos cabeçalhos X-User-* e não validam token

**Contexto.** Com o gateway validando o JWT, validar de novo em cada serviço duplicaria o segredo
e a regra em todos eles.

**Decisão.** O token é validado num lugar só, o Kong. Os serviços leem `X-User-Id` (e o pedidos,
`X-User-Email`) e respondem 401 quando o cabeçalho não vem. O usuarios continua sendo o único que
**emite** o JWT (HS256, com `sub`, `email`, `perfil`, `iss` e `exp`); dele saiu o Spring Security
de validação, ficaram só o encoder de JWT e o BCrypt. As regras de recurso continuam nos serviços:
"este endereço/pedido é seu?" (404) e "este pedido ainda pode ser cancelado?" (409).

**Consequências.** A segurança depende de **nenhum serviço publicar porta**. Quem alcançasse um
serviço direto poderia mandar `X-User-Id` de qualquer pessoa (ver a pergunta 4 da aula 2 em [discussoes.md](../../../discussoes.md)). Se um dia
a rede interna não for confiável, o caminho é mTLS entre serviços (service mesh), fora do escopo.
