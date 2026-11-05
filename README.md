# E-commerce G2 — microserviços

Projeto da disciplina de Arquitetura de Sistemas: um e-commerce em microserviços, construído
uma peça por aula. Parte 1: usuários e catálogo. Parte 2: API Gateway (Kong) e pedidos.
**Parte 3 (esta entrega): Kafka e eventos**: o checkout para de esperar. O pedidos publica
`pedido.criado`, o catálogo reserva o estoque e responde com `estoque.reservado` ou
`estoque.insuficiente`, o pedido muda de status sozinho e o cliente recebe e-mail. Cada serviço tem
o seu banco, e tudo sobe junto com um comando.

| Serviço | Pasta | Porta | Banco | Responsabilidade |
|---|---|---|---|---|
| gateway | [`kong/`](kong) | **8080** (a única da API) | — | Kong: rotas, JWT, perfis, rate limit |
| usuarios | [`usersApi/`](usersApi) | 3000 (interna) | Postgres | cadastro, login (JWT) e endereços |
| catalogo | [`catalog-api/`](catalog-api) | 3000 (interna) | Mongo | produtos, categorias, estoque e reserva |
| pedidos | [`order-api/`](order-api) | 3000 (interna) | Postgres | carrinho, checkout e status do pedido |
| notificacoes | [`notification-api/`](notification-api) | nenhuma | — | só escuta eventos e manda e-mail |
| kafka | — | 9092 (interna) | — | Kafka 3.8 (KRaft, um broker): tópicos `pedidos` e `estoque` |
| kafka-ui | — | 8090 (só nesta máquina) | — | ver tópicos, mensagens, grupos e lag |
| mailpit | — | 8025 (só nesta máquina) | — | SMTP de mentira com caixa de entrada |

Dentro da rede do Docker, todo serviço escuta na 3000 e é chamado pelo nome (`http://catalogo:3000`).
De fora, só o gateway responde. Rodando sem Docker, cada serviço usa a sua porta padrão (3001, 3002,
3003) para os três subirem juntos na mesma máquina.

Stack: Java 21, Spring Boot 4.1, Spring Data JPA / Spring Data MongoDB, Spring for Apache Kafka,
Kafka 3.8 (KRaft), Kong 3.9 (sem banco), Mailpit, Docker Compose.

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

Os serviços esperam o Kafka ficar healthy antes de subir (a primeira subida leva uns 30 s a mais).
Para acompanhar os eventos:

- **Kafka UI**: http://localhost:8090 (tópicos `pedidos` e `estoque`, grupos `pedidos`, `catalogo`
  e `notificacoes`, e o lag de cada um)
- **Mailpit**: http://localhost:8025 (os e-mails que o notificacoes mandou)

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

O id também vai dentro de cada evento (`correlationId`) e segue de evento em evento: quem trata o
evento escreve o log com ele, e os eventos que publica em seguida levam o mesmo id. Uma compra
inteira, do checkout ao e-mail:

```
[pedidos]      [3c37be51-...] Publicado pedido.criado do pedido 6f1c... no tópico pedidos
[catalogo]     [3c37be51-...] Pedido 6f1c...: estoque reservado (2 itens)
[catalogo]     [3c37be51-...] Publicado estoque.reservado do pedido 6f1c... no tópico estoque
[notificacoes] [3c37be51-...] E-mail "Recebemos o seu pedido" enviado para cliente@loja.com
[pedidos]      [3c37be51-...] Pedido 6f1c... agora está AGUARDANDO_PAGAMENTO
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
serviços direto e mande os cabeçalhos que o Kong mandaria (`X-User-Id`, `X-User-Email`). O Kafka
da docker-compose não publica porta: para o catalogo, o pedidos e o notificacoes falarem com
eventos fora do Docker, suba um Kafka local e aponte `KAFKA_BROKERS` para ele (padrão
`localhost:9092`).

### Testes automatizados

```bash
cd usersApi && ./mvnw test      # H2 em memória, não precisa de banco
cd catalog-api && ./mvnw test   # Mongo embutido (baixado na 1ª execução), não precisa de Docker
cd order-api && ./mvnw test     # H2 em memória; o catálogo é simulado (no ar, lento, fora do ar)
cd notification-api && ./mvnw test   # o SMTP é simulado
```

Nenhum teste precisa de Kafka: o `KafkaTemplate` é simulado (o teste confere o que seria
publicado: tópico, chave e envelope) e os listeners são chamados direto com a mensagem, como o
Kafka faria. No catálogo, a reserva roda contra o Mongo embutido, inclusive com 8 pedidos ao mesmo
tempo disputando 3 unidades.

---

## Testando a API: arquivos `.http`

Em [`http/`](http), um arquivo por serviço, com todas as rotas e os casos de erro (400, 401, 403,
404, 409, 503). Todos usam uma URL só, a do gateway (`http://localhost:8080`). No VS Code, instale
a extensão **REST Client** e use o botão **Send Request**.

- [`http/usuarios.http`](http/usuarios.http)
- [`http/catalogo.http`](http/catalogo.http)
- [`http/pedidos.http`](http/pedidos.http): login → carrinho → checkout (202) → reenviar o GET
  até `AGUARDANDO_PAGAMENTO` (caminho feliz); o caminho sem estoque (202 → `CANCELADO` com o
  motivo); e os erros (carrinho vazio, pedido de outra pessoa, catálogo fora)

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
| POST | `/pedidos` | logado | 202 pedido aceito (`AGUARDANDO_ESTOQUE`) · 400 carrinho vazio · 409 produto indisponível · 503 catálogo fora |
| GET | `/pedidos` | logado | 200 meus pedidos |
| GET | `/pedidos/{id}` | logado | 200 pedido com itens e status atual (`motivoCancelamento` se cancelado) · 404 não existe ou não é meu |
| POST | `/pedidos/{id}/cancelar` | logado | 200 cancelado (em `AGUARDANDO_ESTOQUE` ou `AGUARDANDO_PAGAMENTO`) · 404 · 409 status não permite |
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

---

## Eventos

Coreografia: ninguém manda. Cada serviço reage aos eventos que lhe interessam e publica os seus.

```
POST /pedidos → 202
      │
   pedidos ──pedido.criado──► [tópico pedidos] ──► catalogo (reserva tudo ou nada)
      ▲                              ┆                  │
      │                              ┆      estoque.reservado / estoque.insuficiente
      │                              ┆                  ▼
      └──────────────────────────────┆──────── [tópico estoque]
   muda o status                     ┆                  ┆
                                     └┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┴┄┄┄┄► notificacoes ──► e-mail (Mailpit)
```

Linha cheia: quem reage e muda dados. Pontilhada: quem só escuta para avisar.

1. **Checkout**: o pedidos grava o pedido em `AGUARDANDO_ESTOQUE`, publica `pedido.criado` (tópico
   `pedidos`) e responde 202.
2. **Catálogo**: reserva tudo ou nada e publica `estoque.reservado` ou `estoque.insuficiente`
   (tópico `estoque`).
3. **Pedidos**: escuta o tópico `estoque` e muda o status: `AGUARDANDO_PAGAMENTO` ou `CANCELADO`,
   com o motivo.
4. **Notificações**: escuta os dois tópicos e manda e-mail: "Recebemos o seu pedido" e "Seu pedido
   foi cancelado: sem estoque".

| Evento | Tópico | Publica | Consome | dados |
|---|---|---|---|---|
| `pedido.criado` | `pedidos` | pedidos | catalogo, notificacoes | `usuarioId`, `clienteEmail`, `itens` (`produtoId`, `nome`, `precoUnitario`, `quantidade`), `valorTotal`, `formaPagamento`, `cartaoFinal` (só cartão) |
| `estoque.reservado` | `estoque` | catalogo | pedidos (e pagamentos, na próxima aula) | `usuarioId`, `clienteEmail`, `valorTotal`, `formaPagamento`, `cartaoFinal` (só cartão) |
| `estoque.insuficiente` | `estoque` | catalogo | pedidos, notificacoes | `motivo`, `usuarioId`, `clienteEmail` |

- **Envelope**, igual em todos os serviços (a classe `evento/Eventos` é a mesma nos três):

  ```jsonc
  {
    "id": "b3a1...",                      // único por evento
    "tipo": "pedido.criado",
    "pedidoId": "6f1c...",                // também é a chave da mensagem
    "ocorridoEm": "2026-11-05T16:51:04Z", // quando aconteceu, não quando foi lido
    "correlationId": "3c37be51-...",      // o X-Request-Id do Kong
    "dados": { ... }                      // diferente para cada tipo (tabela acima)
  }
  ```

- **Chave de todas as mensagens**: o id do pedido. Os eventos de um pedido caem na mesma partição e
  chegam em ordem.
- **Tópicos**: criados por quem sobe primeiro (cada serviço declara os que usa; o Spring só cria os
  que não existem), com 3 partições.
- **Group ids**: `pedidos`, `catalogo`, `notificacoes`, um por serviço. Réplicas do mesmo serviço
  dividem as partições; grupos diferentes recebem tudo, cada um no seu ritmo.
- **Evento é contrato**: só acrescente campos. Quem lê ignora o que não conhece
  (`@JsonIgnoreProperties(ignoreUnknown = true)`). Precisa quebrar? Publique um tipo novo
  (`pedido.criado.v2`) por um tempo, junto com o antigo.
- **Nunca no evento**: número do cartão, senha, documento. O evento fica guardado no tópico e muita
  gente lê.
- Próxima aula: tópico `pagamentos`, com `pagamento.aprovado` e `pagamento.recusado`.

### O status do pedido

```
                 estoque.reservado                       pagamento.aprovado (próxima aula)
AGUARDANDO_ESTOQUE ───────────────► AGUARDANDO_PAGAMENTO ─────────────────► PAGO
  (nasce no checkout)
        │ estoque.insuficiente
        └──────────────────────────► CANCELADO (motivoCancelamento)
```

Cada seta só vale a partir do estado de origem (`UPDATE ... WHERE id = ? AND status IN (...)`): um
evento repetido ou atrasado não leva o pedido para trás. `POST /pedidos/{id}/cancelar` continua
permitido em `AGUARDANDO_ESTOQUE` e `AGUARDANDO_PAGAMENTO`.

### Limitações conhecidas

| Problema | O que acontece hoje | Como se resolve |
|---|---|---|
| **Escrita dupla** | O pedido é gravado e o Kafka cai antes do `pedido.criado`: o pedido fica parado em `AGUARDANDO_ESTOQUE` para sempre (há um `ERROR` no log com o id). | Padrão **Outbox**: gravar o evento numa tabela na mesma transação do pedido; um publicador lê a tabela e publica até conseguir. |
| **Mensagem venenosa** | Um evento que dá erro sempre é tentado 3 vezes (1 s entre elas) e depois **pulado**; só fica o erro no log. | Mandar para um tópico de erros (**DLQ**) para analisar e reprocessar. |
| **Reserva pela metade** | Se o catálogo cair no meio da reserva de um pedido, a reserva fica `PROCESSANDO`; quando o evento chega de novo, é ignorado (não dá para saber o que já saiu do estoque). | Transação no Mongo envolvendo a reserva e os produtos, ou Outbox também no catálogo. |
| **Cancelado não devolve o estoque** | O cliente cancela um pedido já reservado: o estoque continua em `reservado`. | Saga: `pedido.cancelado` → o catálogo devolve (próxima aula). |
| **E-mail repetido** | Pelo menos uma vez: se o mesmo evento for entregue de novo, sai outro e-mail. | Guardar os `id` de evento já tratados. Para um aviso, é aceitável. |
| **Kafka sem disco próprio e com um broker só** | `docker compose down` apaga os tópicos e os offsets; um broker caído para a mensageria. | Volume para os dados e 3 brokers com fator de replicação 3 (produção). |

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
├── docker-compose.yml        # sobe o gateway, os serviços, os bancos, o Kafka e o Mailpit
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
│       ├── evento/           # Eventos (envelope), PedidosListener, dados do estoque.*
│       ├── exception/  seed/
├── order-api/                # serviço pedidos
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/pedidos/
│       ├── entity/  repository/  dto/  service/  controller/
│       ├── evento/           # Eventos (envelope), EstoqueListener, dados do pedido.criado
│       ├── catalogo/  security/  exception/
└── notification-api/         # serviço notificacoes (sem banco, sem porta)
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/br/com/ecommerce/notificacoes/
        ├── evento/           # Eventos (envelope), NotificacoesListener
        └── email/            # EmailService (SMTP → Mailpit)
```

Cada serviço é independente: seu próprio `pom.xml`, `Dockerfile` e banco. Nenhum importa código
do outro: o que eles compartilham é o formato dos eventos, e por isso a classe `Eventos` é copiada
(igual) em cada um.

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
Valor negativo → 400. O campo `reservado` não é alterado por esta rota: quem mexe nele é a reserva
por evento (ADR-016).

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

**Contexto.** Na compra, o pedidos precisa do preço de agora, e quem sabe disso é o catálogo.
(Até a aula 2, também do estoque; a partir da aula 3, quem decide o estoque é a reserva por
evento, ADR-014.)

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

> **Atualizado pelo ADR-014 (aula 3).** O checkout não confere mais o estoque: o catálogo reserva
> ao receber `pedido.criado`, e o pedido sai de `AGUARDANDO_ESTOQUE` sozinho.

**Decisão.** O checkout confere se cada produto está ativo e tem estoque (409 se não), mas **não
reserva** o estoque: a reserva entra na aula 3, por evento (Kafka). O pedido é gravado junto com a
limpeza do carrinho, numa transação só, e a resposta é **202 Accepted** com `Location`.

**Consequências.** Entre o checkout e a reserva, dois clientes podem comprar a última unidade. Na
aula 3, o catálogo reserva ao receber o evento `pedido.criado` e o pedido sai de
`AGUARDANDO_ESTOQUE` sozinho. O cliente pode cancelar enquanto o pedido não foi pago (409 depois).

### ADR-014 — Checkout por eventos (Kafka), em coreografia

**Contexto.** Com o checkout em corrente (pedidos → catálogo → pagamento → e-mail), cada serviço a
mais multiplica a chance de falhar e soma latência, e o pedidos precisa conhecer todos.

**Decisão.** Kafka 3.8 em modo KRaft (um container, broker e controller, sem ZooKeeper). O
checkout publica `pedido.criado` e responde 202; o resto acontece por eventos (seção
[Eventos](#eventos)). Coreografia: o pedidos não sabe que o catálogo reserva, só reage ao
resultado; o notificacoes só escuta. A consulta síncrona ao catálogo continua no checkout, mas só
para o preço (ADR-011): o preço precisa ser o de agora. O checkout não confere mais o estoque
(quem decide é a reserva), então o "sem estoque" deixou de ser 409 e virou `CANCELADO` com motivo.

**Consequências.** Consistência eventual: por alguns instantes, o pedido existe e o estoque ainda
não foi reservado; o mesmo `GET /pedidos/{id}` responde diferente em instantes diferentes. O fluxo
inteiro só aparece somando todos os serviços (por isso o `correlationId` em todos os eventos e
logs). Um serviço caído não derruba a compra: os eventos esperam no tópico. Desfazer passos quando
algo falha no meio é a saga, na próxima aula.

### ADR-015 — Publicar depois do commit, sem Outbox

**Decisão.** O `pedido.criado` é publicado **depois** do commit da transação que grava o pedido e
limpa o carrinho: nunca se anuncia um pedido que pode sumir num rollback. O envio espera a
confirmação do Kafka (até 10 s; o produtor desiste de esperar metadados em 5 s). Se falhar, o
checkout responde 202 mesmo assim (o pedido existe) e escreve um `ERROR` no log com o id.

**Consequências.** Escrita dupla: se o Kafka cair entre o commit e o publicar, o pedido fica parado
em `AGUARDANDO_ESTOQUE`. A solução (Outbox) fica como limitação conhecida.

### ADR-016 — Idempotência: reservas por pedido e filtro de estado

**Contexto.** O Kafka entrega pelo menos uma vez: o offset só é confirmado depois que o listener
termina, então uma falha no meio faz o mesmo evento chegar de novo.

**Decisão.** No **catálogo**, a coleção `reservas` tem índice único em `pedidoId`: a reserva é
gravada antes de tirar qualquer estoque, e o segundo `pedido.criado` do mesmo pedido dá chave
duplicada e não reserva de novo; se a reserva já tem resultado, ele é republicado (cobre o caso de
a publicação ter falhado). Cada item sai do estoque com um update só, com
`estoque.disponivel >= quantidade` no filtro (`$inc` em `disponivel` e `reservado`): dois pedidos
ao mesmo tempo não deixam o estoque negativo. Faltou um item, os já reservados são devolvidos
(tudo ou nada). No **pedidos**, o status só muda a partir do estado esperado, num `UPDATE`
condicional; o cancelamento pelo cliente usa o mesmo `UPDATE`.

**Consequências.** Reservado não é vendido: o pagamento (próxima aula) confirma a venda ou devolve
ao disponível. O `PATCH /produtos/{id}/estoque` continua definindo o disponível e não mexe no
reservado.

### ADR-017 — Spring for Apache Kafka no lugar do kafkajs dos slides

**Contexto.** Os exemplos da aula usam Node (kafkajs, nodemailer); os serviços deste projeto são
Spring Boot.

**Decisão.** `spring-boot-starter-kafka` com `KafkaTemplate<String, String>` e `@KafkaListener`: o
valor da mensagem é texto (JSON), lido e escrito pela classe `Eventos` de cada serviço, que faz o
papel do `kafka.js` dos slides. Tópicos declarados como `NewTopic` (o Spring faz o `listTopics` +
`createTopics`). `auto-offset-reset=earliest` equivale ao `fromBeginning` (só vale para grupo novo).
O Spring confirma o offset depois que o listener termina, como o kafkajs. Erro no listener: mais 2
tentativas com 1 s entre elas e depois segue (sem DLQ). Os e-mails saem com
`spring-boot-starter-mail` para o Mailpit. No compose, `stop_grace_period: 10s` dá tempo de o
consumer sair do grupo ao parar.

**Consequências.** Mesma semântica dos slides (pelo menos uma vez, chave = pedido, um grupo por
serviço), com o ecossistema que o projeto já usa. O notificacoes é um Spring Boot sem web
(`web-application-type=none`): fica no ar enquanto o listener roda.

---

## Para discutir em dupla (aula 3: Kafka e eventos)

**1. O checkout ainda consulta o catálogo de forma síncrona. Dá para tirar essa chamada também? O
que se perde?** Dá: o pedidos poderia guardar uma cópia dos preços, atualizada por eventos do
catálogo (`produto.atualizado`), e o checkout nem precisaria do catálogo no ar. Perde-se a
garantia de que o preço é o de agora: a cópia pode estar alguns instantes atrasada, e a loja
venderia pelo preço antigo logo depois de um reajuste. Também se perde o 409 imediato de "produto
indisponível": ele viraria um cancelamento depois, como o sem estoque. É a troca de sempre:
disponibilidade contra consistência.

**2. O Kafka caiu entre o COMMIT do pedido e o publicar. O que acontece com esse pedido?** Ele
existe no banco, em `AGUARDANDO_ESTOQUE`, e ninguém fica sabendo: o catálogo não reserva, o e-mail
não sai, e o pedido fica parado para sempre (o cliente vê "processando"). Hoje só sobra o `ERROR`
no log. É a escrita dupla; a solução é o Outbox (ver [Limitações](#limitações-conhecidas)).

**3. Por que a chave das mensagens é o id do pedido e não o id do usuário?** O Kafka só garante
ordem dentro de uma partição, e o que precisa de ordem são os eventos **de um mesmo pedido** (o
`pedido.cancelado` não pode chegar antes do `pedido.criado`). Com o id do usuário, a ordem por
pedido também valeria, mas todos os pedidos de um cliente grande (um revendedor, um bot) cairiam
na mesma partição: ela viraria gargalo e pedidos independentes esperariam uns pelos outros. O id
do pedido espalha melhor e dá a ordem que importa.

**4. O catálogo tem 3 réplicas e o tópico pedidos, 3 partições. E se subirmos 5 réplicas?** Cada
partição é lida por um só consumer do grupo: 3 réplicas trabalham e 2 ficam paradas, de reserva
(assumem uma partição se alguma réplica cair). Para mais paralelismo, mais partições; cuidado que
aumentar partições muda para qual partição vai cada chave dali em diante.

**5. O cliente cancelou um pedido que já estava com o estoque reservado. Quem devolve o
estoque?** Hoje, ninguém: o pedido fica `CANCELADO` e as unidades continuam em `reservado` no
catálogo. O pedidos teria que publicar `pedido.cancelado`, e o catálogo, ao receber, devolveria a
reserva daquele pedido ao disponível. Desfazer um passo anterior com outro evento é a saga, assunto
da próxima aula.

## Para discutir em dupla (aula 2: gateway e pedidos)

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
