# ADR-003 — JWT com Spring Security, validado no próprio serviço usuarios

> **Substituído pelo ADR-009 (aula 2).** O usuarios continua emitindo o token, mas quem valida é
> o gateway.

**Contexto.** O login devolve um JWT com id, e-mail e perfil, assinado com `JWT_SECRET`. Até o
gateway existir, o próprio serviço valida o token nas rotas `/me`.

**Decisão.** Spring Security com o OAuth2 Resource Server: a mesma chave HS256 (`JWT_SECRET`, com
pelo menos 32 caracteres) gera o token no login (`JwtEncoder`) e o valida nas rotas protegidas
(`JwtDecoder`). A claim `perfil` vira o papel (`ROLE_admin`/`ROLE_cliente`) usado em
`GET /usuarios`. Senhas com BCrypt. Os 401/403 do Spring Security são repassados ao handler de
erros para sair no mesmo formato JSON do resto da API.

**Consequências.** Sem biblioteca extra de JWT. Na aula 2 o gateway passa a validar o token com o
mesmo segredo, e esta configuração pode ser simplificada.
