# ADR-003 — API Gateway com Kong sem banco (`kong/kong.yml`)

**Contexto.** Na aula 1, cada serviço tinha a sua porta, o cliente precisava conhecer todas, e as
rotas de admin do catálogo estavam abertas.

**Decisão.** Kong 3.9 no modo declarativo: toda a configuração num arquivo,
[`kong/kong.yml`](../../kong/kong.yml), versionado no Git, sem banco para o gateway. A 8080 é a única
porta da API. Os níveis de acesso viram plugins por route: **público** (nenhum), **logado**
(`jwt`) e **admin** (`jwt` + `acl allow [admin]`). Plugins globais: `correlation-id`
(X-Request-Id), `pre-function` (apaga os X-User-* que vierem de fora) e `post-function` (escreve
os X-User-* a partir do token). O login tem `rate-limiting` de 10 por minuto por IP.

Os consumers são **perfis, não pessoas**: `cliente` e `admin`. O `jwt` acha o consumer pelo
campo `perfil` do token (`key_claim_name: perfil`) e o `acl` confere o grupo dele. Assim o
usuarios não precisa cadastrar cada cliente no Kong.

**Consequências.** Mudar uma rota é mudar um arquivo e rodar `docker compose restart gateway`;
não dá para alterar a configuração pela Admin API em tempo de execução. Perfil novo (ex.:
estoquista) é um consumer e um grupo novos no `kong.yml`. Mudança de perfil no banco só vale no
próximo login (o token antigo vale até vencer, em 2 h). O gateway é ponto único de falha: em
produção, mais de uma réplica atrás de um balanceador. Os erros que o Kong responde vêm no formato
dele (`{ "message" }`), não no `{ erro, detalhes }` dos serviços.
