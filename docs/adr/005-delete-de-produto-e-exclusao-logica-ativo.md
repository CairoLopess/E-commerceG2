# ADR-005 — DELETE de produto é exclusão lógica (`ativo = false`)

**Decisão.** `DELETE /produtos/{id}` não apaga o documento: marca `ativo = false`. O produto some
da vitrine (`GET` responde 404), mas continua no banco e pode ser reativado com um `PUT`
contendo `"ativo": true`.

**Consequências.** Pedidos futuros (aula 2+) continuam podendo referenciar produtos que saíram de
linha.
