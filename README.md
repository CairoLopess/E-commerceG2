# E-commerce G2 — microserviços

Projeto da disciplina de Arquitetura de Sistemas: um e-commerce em microserviços, construído
uma peça por aula. Parte 1: usuários e catálogo. **Parte 2 (esta entrega): API Gateway (Kong) e
pedidos**: uma porta só para a loja inteira, rotas protegidas por token e perfil, e o carrinho com
checkout. Cada serviço tem o seu banco, e tudo sobe junto com um comando.

| Serviço | Pasta | Porta | Banco | Responsabilidade |
|---|---|---|---|---|
| gateway | [`kong/`](kong) | **8080** (a única publicada) | — | Kong: rotas, JWT, perfis, rate limit |
| usuarios | [`usersApi/`](usersApi) | 3000 (interna) | Postgres | cadastro, login (JWT) e endereços |
| catalogo | [`catalog-api/`](catalog-api) | 3000 (interna) | Mongo | produtos, categorias e estoque |
| pedidos | [`order-api/`](order-api) | 3000 (interna) | Postgres | carrinho e checkout |

Dentro da rede do Docker, todo serviço escuta na 3000 e é chamado pelo nome (`http://catalogo:3000`).
De fora, só o gateway responde. Rodando sem Docker, cada serviço usa a sua porta padrão (3001, 3002,
3003) para os três subirem juntos na mesma máquina.

Stack: Java 21, Spring Boot 4.1, Spring Data JPA / Spring Data MongoDB, Kong 3.9 (sem banco),
Docker Compose.

---

## Como subir

Pré-requisito: Docker com o Compose.

```bash
cp .env.example .env      # e troque o JWT_SECRET (mínimo 32 caracteres)
docker compose up --build
```

Pronto quando estes três respondem `200` (pelo gateway):

- http://localhost:8080/health/usuarios
- http://localhost:8080/health/catalogo
- http://localhost:8080/health/pedidos

Para conferir a configuração do Kong: `curl localhost:8001/routes` ou o Kong Manager em
http://localhost:8002. Mudou o [`kong/kong.yml`](kong/kong.yml)? `docker compose restart gateway`.

Para zerar os bancos (apaga os dados e roda o seed de novo): `docker compose down -v`.

### Rastreando uma requisição

O gateway dá um `X-Request-Id` a cada requisição, repassa a todos os serviços e devolve na
resposta. Cada serviço escreve uma linha por requisição com o id entre colchetes, e toda linha de
log escrita durante ela também leva o id (ex.: o aviso de catálogo fora, no pedidos):

```
INFO --- [pedidos] [nio-3000-exec-1] [3c37be51-...] b.c.e.pedidos.config.RequestIdFilter : POST /pedidos -> 202 (48 ms)
INFO --- [catalogo] [nio-3000-exec-3] [3c37be51-...] b.c.e.catalogo.config.RequestIdFilter : GET /produtos -> 200 (9 ms)
```

Com o id da resposta (ou de um chamado de suporte), um grep acha a história inteira:
`docker compose logs | grep 3c37be51`.

### Usuários do seed

| E-mail | Senha | Perfil |
|---|---|---|
| `admin@loja.com` | `admin123` | admin |
| `cliente@loja.com` | `cliente123` | cliente (com 1 endereço) |

O catálogo nasce com 3 categorias (`celulares`, `roupas`, `livros`) e 12 produtos.
Os seeds são idempotentes: subir de novo não duplica nada.

### Rodar um serviço sem Docker (desenvolvimento)

Com um Postgres e/ou Mongo locais, preencha o `.env` (os serviços leem o `.env` da raiz
automaticamente) e, dentro da pasta do serviço:

```bash
./mvnw spring-boot:run
```

Os bancos `usuarios` e `pedidos` precisam existir no Postgres (`CREATE DATABASE usuarios;`,
`CREATE DATABASE pedidos;`); as tabelas são criadas na subida. Sem Docker não há gateway: chame os
serviços direto e mande os cabeçalhos que o Kong mandaria (`X-User-Id`, `X-User-Email`).

### Testes automatizados

```bash
cd usersApi && ./mvnw test      # H2 em memória, não precisa de banco
cd catalog-api && ./mvnw test   # Mongo embutido (baixado na 1ª execução), não precisa de Docker
cd order-api && ./mvnw test     # H2 em memória; o catálogo é simulado (no ar, lento, fora do ar)
```

---

## Testando a API: arquivos `.http`

Em [`http/`](http), um arquivo por serviço, com todas as rotas e os casos de erro (400, 401, 403,
404, 409, 503). Todos usam uma URL só, a do gateway (`http://localhost:8080`). No VS Code, instale
a extensão **REST Client** e use o botão **Send Request**.

- [`http/usuarios.http`](http/usuarios.http)
- [`http/catalogo.http`](http/catalogo.http)
- [`http/pedidos.http`](http/pedidos.http): login → carrinho → checkout (202) → consultar o
  pedido, e os erros (carrinho vazio, pedido de outra pessoa, estoque, catálogo fora)

Rode de cima para baixo: as requisições marcadas com `# @name` guardam a resposta (token, ids)
para as seguintes.

---

## Rotas

Todas pelo gateway (`http://localhost:8080`). O acesso é conferido pelo Kong: **logado** = token
válido (401 se não); **admin** = token válido com perfil admin (403 se for cliente).

### usuarios

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| POST | `/auth/login` | público | 200 `{ token, usuario }` · 401 · 429 na 11ª tentativa no minuto |
| POST | `/usuarios` | público | 201 · 400 · 409 e-mail já existe |
| GET | `/usuarios/me` | logado | 200 · 401 |
| PUT | `/usuarios/me` | logado | 200 · 400 · 409 |
| GET | `/usuarios/me/enderecos` | logado | 200 |
| POST | `/usuarios/me/enderecos` | logado | 201 · 400 |
| DELETE | `/usuarios/me/enderecos/{id}` | logado | 204 · 404 não existe ou não é meu |
| GET | `/usuarios` | admin | 200 · 401 · 403 |
| GET | `/health/usuarios` | público | 200 · 503 |

### catalogo

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| GET | `/produtos?categoria=&busca=&ids=` | público | 200 (só ativos; `ids=a,b,c` é a chamada do checkout) |
| GET | `/produtos/{id}` | público | 200 · 404 |
| POST | `/produtos` | admin | 201 · 400 · 401 · 403 · 409 SKU já existe |
| PUT | `/produtos/{id}` | admin | 200 · 400 · 401 · 403 · 404 · 409 |
| PATCH | `/produtos/{id}/estoque` | admin | 200 · 400 estoque negativo · 401 · 403 · 404 |
| DELETE | `/produtos/{id}` | admin | 204 desativado · 401 · 403 · 404 |
| GET | `/categorias` | público | 200 |
| POST | `/categorias` | admin | 201 · 400 · 401 · 403 · 409 slug já existe |
| GET | `/health/catalogo` | público | 200 · 503 |

As rotas de admin do catálogo, abertas na aula 1 (ADR-004), agora são fechadas pelo gateway.

### pedidos

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| GET | `/carrinho` | logado | 200 `{ itens, total }` com preços atuais do catálogo (catálogo fora: itens sem preço + `aviso`) |
| POST | `/carrinho/itens` | logado | 201 item adicionado (soma se já estiver) · 400 quantidade inválida · 404 produto não existe · 503 |
| DELETE | `/carrinho/itens/{produtoId}` | logado | 204 removido · 404 não está no carrinho |
| POST | `/pedidos` | logado | 202 pedido aceito · 400 carrinho vazio · 409 produto indisponível ou sem estoque · 503 catálogo fora |
| GET | `/pedidos` | logado | 200 meus pedidos |
| GET | `/pedidos/{id}` | logado | 200 pedido com itens · 404 não existe ou não é meu |
| POST | `/pedidos/{id}/cancelar` | logado | 200 cancelado · 404 · 409 status não permite |
| GET | `/health/pedidos` | público | 200 · 503 |

Corpo do checkout (os itens vêm do carrinho):

```json
{
  "enderecoEntrega": { "cep": "90010-000", "logradouro": "Rua A", "numero": "100",
                       "cidade": "Porto Alegre", "uf": "RS" },
  "formaPagamento": "cartao",
  "numeroCartao": "4111111111111111"
}
```

`formaPagamento`: `cartao`, `pix` ou `boleto`. `numeroCartao` só para cartão; o pedido guarda só
os 4 últimos dígitos.

### Formato de erro

Os três serviços respondem no mesmo formato:

```json
{ "erro": "Dados inválidos.", "detalhes": ["preco deve ser maior que zero", "sku é obrigatório"] }
```

Nunca sai stack trace, mensagem do banco ou `senha_hash`. Os erros que o próprio Kong responde
(401 de token, 403 de perfil, 429 de limite, 404 de rota que não existe) vêm no formato dele:
`{ "message": "..." }`.

---

## Estrutura

```
.
├── docker-compose.yml        # sobe o gateway, os serviços e os bancos
├── kong/kong.yml             # configuração do gateway (Kong sem banco)
├── .env.example              # variáveis (copie para .env)
├── http/                     # requisições de teste (REST Client)
├── usersApi/                 # serviço usuarios
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/usuarios/
│       ├── entity/  repository/  dto/  service/  controller/
│       ├── config/  security/  exception/  seed/
├── catalog-api/              # serviço catalogo
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/catalogo/
│       ├── document/  repository/  dto/  service/  controller/
│       ├── exception/  seed/
└── order-api/                # serviço pedidos
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/br/com/ecommerce/pedidos/
        ├── entity/  repository/  dto/  service/  controller/
        ├── catalogo/  security/  exception/
```

Cada serviço é independente: seu próprio `pom.xml`, `Dockerfile` e banco. Nenhum importa código
do outro.

---

## Decisões de arquitetura (ADRs)

### ADR-001 — Persistência poliglota: Postgres no usuarios, Mongo no catalogo

**Contexto.** Usuários têm estrutura fixa (nome, e-mail, senha, perfil), e-mail que não pode
repetir e endereços que pertencem a um usuário. Produtos têm atributos diferentes por categoria
(celular: memória e cor; camiseta: tamanho e tecido; livro: autor e páginas), e a vitrine é muito
mais lida que escrita.

**Decisão.** Cada serviço escolhe o seu banco. **usuarios → Postgres**: o banco garante o e-mail
único (UNIQUE) e o vínculo do endereço com o usuário (FK com `ON DELETE CASCADE`).
**catalogo → Mongo**: cada produto é um documento com os seus próprios `atributos`, sem uma coluna
para cada atributo possível.

**Consequências.** Dois bancos para operar. Não existe JOIN entre usuário e produto, e nem deve
existir: cada serviço só acessa o próprio banco.

### ADR-002 — Esquema do Postgres gerado pelo Hibernate (`ddl-auto=update`), sem migrations

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

### ADR-003 — JWT com Spring Security, validado no próprio serviço usuarios

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

### ADR-004 — Rotas de admin do catálogo abertas até a aula 2

> **Resolvido pelo ADR-008 (aula 2).** As rotas de admin agora exigem token com perfil admin no
> gateway.

**Contexto.** O catálogo não deveria saber validar o token emitido por outro serviço.

**Decisão.** `POST/PUT/PATCH/DELETE` de produtos e `POST /categorias` ficam sem autenticação nesta
entrega, como pede o enunciado.

**Consequências.** Qualquer um pode alterar o catálogo enquanto não houver gateway. Aceitável só
em ambiente de aula. Resolvido na aula 2.

### ADR-005 — DELETE de produto é exclusão lógica (`ativo = false`)

**Decisão.** `DELETE /produtos/{id}` não apaga o documento: marca `ativo = false`. O produto some
da vitrine (`GET` responde 404), mas continua no banco e pode ser reativado com um `PUT`
contendo `"ativo": true`.

**Consequências.** Pedidos futuros (aula 2+) continuam podendo referenciar produtos que saíram de
linha.

### ADR-006 — `PATCH /produtos/{id}/estoque` define o valor disponível

**Decisão.** O corpo `{ "disponivel": 25 }` **define** o estoque disponível (não soma ao atual).
Valor negativo → 400. O campo `reservado` não é alterado por esta rota: ele passa a ser usado na
aula 3, quando o pedido reservar estoque por evento.

**Consequências.** A operação é idempotente (repetir a mesma requisição dá o mesmo resultado).
Ajustes relativos (+/-) ficam para quando houver reserva de estoque.

### ADR-007 — Preço como `BigDecimal` / `Decimal128`

**Decisão.** O preço é `BigDecimal` no Java e `Decimal128` no Mongo, com no máximo 2 casas
decimais e sempre maior que zero.

**Consequências.** Sem erros de arredondamento de `double` em valores monetários, o que importa
quando os pedidos começarem a somar preços.

### ADR-008 — API Gateway com Kong sem banco (`kong/kong.yml`)

**Contexto.** Na aula 1, cada serviço tinha a sua porta, o cliente precisava conhecer todas, e as
rotas de admin do catálogo estavam abertas.

**Decisão.** Kong 3.9 no modo declarativo: toda a configuração num arquivo,
[`kong/kong.yml`](kong/kong.yml), versionado no Git, sem banco para o gateway. A 8080 é a única
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

### ADR-009 — Os serviços confiam nos cabeçalhos X-User-* e não validam token

**Contexto.** Com o gateway validando o JWT, validar de novo em cada serviço duplicaria o segredo
e a regra em todos eles.

**Decisão.** O token é validado num lugar só, o Kong. Os serviços leem `X-User-Id` (e o pedidos,
`X-User-Email`) e respondem 401 quando o cabeçalho não vem. O usuarios continua sendo o único que
**emite** o JWT (HS256, com `sub`, `email`, `perfil`, `iss` e `exp`); dele saiu o Spring Security
de validação, ficaram só o encoder de JWT e o BCrypt. As regras de recurso continuam nos serviços:
"este endereço/pedido é seu?" (404) e "este pedido ainda pode ser cancelado?" (409).

**Consequências.** A segurança depende de **nenhum serviço publicar porta**. Quem alcançasse um
serviço direto poderia mandar `X-User-Id` de qualquer pessoa (ver a pergunta 4, abaixo). Se um dia
a rede interna não for confiável, o caminho é mTLS entre serviços (service mesh), fora do escopo.

### ADR-010 — Todo serviço escuta na porta 3000 dentro da rede

**Decisão.** No compose, `PORT=3000` em todos os serviços, que se chamam pelo nome
(`http://catalogo:3000`). Sem `ports:` publicadas, cada container tem a sua rede e não há
conflito. Fora do Docker, os padrões continuam 3001, 3002 e 3003, para os três rodarem juntos na
mesma máquina.

**Consequências.** Uma convenção só, igual ao `kong.yml` de referência da aula. Serviço novo não
precisa de porta nova.

### ADR-011 — O checkout chama o catálogo direto, de forma síncrona, com timeout de 3 s

**Contexto.** Na compra, o pedidos precisa do preço e do estoque de agora, e quem sabe disso é o
catálogo.

**Decisão.** `GET http://catalogo:3000/produtos?ids=a,b,c`: uma chamada para todos os produtos do
carrinho, pela rede interna, sem passar pelo gateway, repassando o `X-Request-Id`. Timeout de
conexão e de leitura de 3 s. Fora do ar, lento ou com erro → **503** no checkout. O carrinho
degrada em vez de falhar: com o catálogo fora, `GET /carrinho` devolve produto e quantidade, sem
preço, com um `aviso`. A lista de pedidos não depende do catálogo.

**Consequências.** Acoplamento temporal: para vender, o catálogo precisa estar no ar naquele
instante. `docker compose stop catalogo` derruba o checkout, mas não o resto do pedidos. O
`pedidos` não tem `depends_on` no catálogo de propósito.

### ADR-012 — O pedido copia nome, preço e endereço; sem FK para outros serviços

**Decisão.** Três tabelas no Postgres do pedidos: `carrinho_itens` (chave `usuario_id` +
`produto_id`, `CHECK quantidade > 0`), `pedidos` (status com `CHECK` nos 4 estados,
`endereco_entrega` em JSONB) e `itens_pedido` (FK para `pedidos`, que está no mesmo banco). Sem FK
para usuários e produtos, que moram em outros bancos: quem garante que existem é o código,
perguntando ao serviço dono. No checkout, nome e preço de cada item são copiados do catálogo, e o
endereço vem no corpo da requisição e é gravado dentro do pedido. Do cartão, só os 4 últimos
dígitos.

**Consequências.** O pedido de hoje não muda se o produto mudar de preço amanhã, ou se o cliente
mudar de endereço: é histórico, não duplicação. O checkout não precisa chamar o usuarios. Como no
usuarios, o esquema é gerado pelo Hibernate (`ddl-auto=update`, ver ADR-002).

### ADR-013 — Checkout responde 202 e o pedido nasce em `AGUARDANDO_ESTOQUE`

**Decisão.** O checkout confere se cada produto está ativo e tem estoque (409 se não), mas **não
reserva** o estoque: a reserva entra na aula 3, por evento (Kafka). O pedido é gravado junto com a
limpeza do carrinho, numa transação só, e a resposta é **202 Accepted** com `Location`.

**Consequências.** Entre o checkout e a reserva, dois clientes podem comprar a última unidade. Na
aula 3, o catálogo reserva ao receber o evento `pedido.criado` e o pedido sai de
`AGUARDANDO_ESTOQUE` sozinho. O cliente pode cancelar enquanto o pedido não foi pago (409 depois).

---

## Para discutir em dupla

**1. O pedidos deveria chamar o catálogo pelo gateway (`localhost:8080`)?** Não. Ganharia o
rate limit e os logs do gateway também nessa chamada, mas perderia mais: um salto de rede a mais
(mais latência), o gateway viraria dependência de uma conversa interna, e a chamada precisaria de
um token, que o pedidos não tem. O gateway é a porta de **fora**. Dentro da rede, serviço fala com
serviço direto, e o `X-Request-Id` é repassado para manter o rastreamento.

**2. O gateway caiu. O que para?** A loja inteira, para o cliente: nenhuma rota responde, mesmo
com todos os serviços de pé. Por isso, em produção, o gateway roda com várias réplicas. **E se for
o catálogo?** Para a vitrine e o checkout (503), e o carrinho mostra os itens sem preço. Login,
cadastro, endereços, o próprio carrinho e a lista de pedidos continuam funcionando.

**3. Por que o pedido copia o preço em vez de guardar só o `produto_id`?** Porque o pedido é um
registro do que foi combinado no momento da compra. Se guardasse só o id, um reajuste de preço
amanhã mudaria o valor de um pedido de ontem, e um produto desativado deixaria o pedido sem nome.
Além disso, mostrar um pedido não dependeria do catálogo estar no ar.

**4. Um aluno publicou a porta 3003 do pedidos "para testar". Qual o risco?** O pedidos confia
em `X-User-Id`, sem validar token. Pela 3003, qualquer pessoa manda
`X-User-Id: <id de outra pessoa>` e vê os pedidos dela, cancela, faz compras no nome dela. O
gateway (que apagaria esse cabeçalho) é simplesmente contornado. Para testar, use o gateway, ou
`docker compose exec`, ou rode o serviço localmente fora do compose.

**5. Onde colocar um limite de 10 requisições por segundo por cliente?** No gateway, com o
plugin `rate-limiting` (`second: 10`), antes de a requisição gastar qualquer serviço. O cuidado
está no "por cliente": `limit_by: consumer` contaria **por perfil**, e todos os clientes
dividiriam o mesmo limite. As opções são:
- `limit_by: ip`, que é simples, mas faz clientes atrás da mesma rede dividirem o limite;
- `limit_by: header` com `X-User-Id`, desde que o cabeçalho já esteja escrito quando o
  rate-limiting roda (prioridade 910). Hoje quem escreve é o `post-function` (−1000), depois dele,
  então a escrita teria que ir para o `pre-function`. Ele pode ler o token antes da validação,
  porque o `jwt` (1450) roda em seguida e recusa os falsos.

Com várias réplicas do Kong, a contagem tem que ficar num lugar só (`policy: redis`), senão cada
réplica conta separado.
