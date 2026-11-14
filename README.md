# E-commerce G2 — microserviços

Projeto da disciplina de Arquitetura de Sistemas: um e-commerce em microserviços, construído
uma peça por aula. Parte 1: usuários e catálogo. Parte 2: API Gateway (Kong) e pedidos. Parte 3:
Kafka e eventos. **Parte 4 (esta entrega): saga e resiliência**: o pagamento entra na compra. O
pagamentos cobra num banco de cartão de mentira (o banco-falso, que demora e falha quando a gente
manda), com timeout, retry e circuit breaker. Quando algo dá errado, cada serviço desfaz a própria
parte: o catálogo devolve o estoque, o pedido vira `CANCELADO` com o motivo, o pagamento é estornado
e o cliente recebe e-mail. Cada serviço tem o seu banco, e tudo sobe junto com um comando.

| Serviço | Pasta | Porta | Banco | Responsabilidade |
|---|---|---|---|---|
| gateway | [`kong/`](kong) | **8080** (a única da API) | — | Kong: rotas, JWT, perfis, rate limit |
| usuarios | [`usersApi/`](usersApi) | 3000 (interna) | Postgres | cadastro, login (JWT) e endereços |
| catalogo | [`catalog-api/`](catalog-api) | 3000 (interna) | Mongo | produtos, categorias, estoque e reserva |
| pedidos | [`order-api/`](order-api) | 3000 (interna) | Postgres | carrinho, checkout, status e cancelamento do pedido |
| pagamentos | [`payment-api/`](payment-api) | 3000 (interna) | Postgres | cobra no banco (timeout, retry, circuit breaker) e estorna |
| notificacoes | [`notification-api/`](notification-api) | nenhuma | — | só escuta eventos e manda e-mail |
| banco-falso | [`fake-bank/`](fake-bank) | **9000** (fora do Kong) | — (memória) | o banco do cartão, sistema externo: recusa, demora e falha sob comando |
| kafka | — | 9092 (interna) | — | Kafka 3.8 (KRaft, um broker): tópicos `pedidos`, `estoque` e `pagamentos` |
| kafka-ui | — | 8090 (só nesta máquina) | — | ver tópicos, mensagens, grupos e lag |
| mailpit | — | 8025 (só nesta máquina) | — | SMTP de mentira com caixa de entrada |

Dentro da rede do Docker, todo serviço escuta na 3000 e é chamado pelo nome (`http://catalogo:3000`).
De fora, só o gateway responde (e o banco-falso, na 9000, que faz o papel de um sistema que não é
nosso). Rodando sem Docker, cada serviço usa a sua porta padrão (3001, 3002, 3003, 3004 e o
banco-falso na 9000) para todos subirem juntos na mesma máquina.

Stack: Java 21, Spring Boot 4.1, Spring Data JPA / Spring Data MongoDB / JDBC, Spring for Apache
Kafka, Resilience4j (circuit breaker), Kafka 3.8 (KRaft), Kong 3.9 (sem banco), Mailpit, Docker
Compose.

---

## Como subir

Pré-requisito: Docker com o Compose.

```bash
cp .env.example .env      # e troque o JWT_SECRET (mínimo 32 caracteres)
docker compose up --build
```

Pronto quando estes quatro respondem `200` (pelo gateway):

- http://localhost:8080/health/usuarios
- http://localhost:8080/health/catalogo
- http://localhost:8080/health/pedidos
- http://localhost:8080/health/pagamentos (com `"circuitoBancoExterno": "fechado"`)

E o banco-falso, direto: http://localhost:9000/config (`{ "latenciaMs": 0, "falhaPercent": 0 }`).

Os serviços esperam o Kafka ficar healthy antes de subir (a primeira subida leva uns 30 s a mais).
Para acompanhar os eventos:

- **Kafka UI**: http://localhost:8090 (tópicos `pedidos`, `estoque` e `pagamentos`; grupos
  `pedidos`, `catalogo`, `pagamentos` e `notificacoes`, e o lag de cada um)
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
[pagamentos]   [3c37be51-...] Pedido 6f1c...: tentativa 1/3 falhou: HTTP 503
[pagamentos]   [3c37be51-...] Pedido 6f1c...: tentativa 2/3 aprovada (TX-F6A1B2C3)
[pagamentos]   [3c37be51-...] Publicado pagamento.aprovado do pedido 6f1c... no tópico pagamentos
[catalogo]     [3c37be51-...] Pedido 6f1c...: venda confirmada, 2 itens saíram do reservado
[pedidos]      [3c37be51-...] Pedido 6f1c... agora está PAGO
[notificacoes] [3c37be51-...] E-mail "Pagamento aprovado: pedido confirmado" enviado para cliente@loja.com
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

Os bancos `usuarios`, `pedidos` e `pagamentos` precisam existir no Postgres
(`CREATE DATABASE usuarios;` e assim por diante); as tabelas são criadas na subida. O banco-falso
não precisa de nada: `cd fake-bank && ./mvnw spring-boot:run` sobe na 9000. Sem Docker não há gateway: chame os
serviços direto e mande os cabeçalhos que o Kong mandaria (`X-User-Id`, `X-User-Email`,
`X-User-Role`). O Kafka da docker-compose não publica porta: para os serviços falarem com
eventos fora do Docker, suba um Kafka local e aponte `KAFKA_BROKERS` para ele (padrão
`localhost:9092`).

### Testes automatizados

```bash
cd usersApi && ./mvnw test      # H2 em memória, não precisa de banco
cd catalog-api && ./mvnw test   # Mongo embutido (baixado na 1ª execução), não precisa de Docker
cd order-api && ./mvnw test     # H2 em memória; o catálogo é simulado (no ar, lento, fora do ar)
cd notification-api && ./mvnw test   # o SMTP é simulado
cd payment-api && ./mvnw test   # H2 em memória; o banco do cartão é simulado (aprova, recusa, 503, timeout)
cd fake-bank && ./mvnw test     # as regras do banco-falso (0002, idempotência, /config, estornos)
```

Nenhum teste precisa de Kafka: o `KafkaTemplate` é simulado (o teste confere o que seria
publicado: tópico, chave e envelope) e os listeners são chamados direto com a mensagem, como o
Kafka faria. No catálogo, a reserva roda contra o Mongo embutido, inclusive com 8 pedidos ao mesmo
tempo disputando 3 unidades. No pagamentos, o retry e o circuit breaker rodam de verdade contra o
banco simulado (o circuito abre, passa a meio-aberto e fecha), e o `BancoClient` é testado contra um
servidor HTTP do próprio JDK, inclusive o timeout.

---

## Testando a API: arquivos `.http`

Em [`http/`](http), um arquivo por serviço, com todas as rotas e os casos de erro (400, 401, 403,
404, 409, 503). Todos usam uma URL só, a do gateway (`http://localhost:8080`). No VS Code, instale
a extensão **REST Client** e use o botão **Send Request**.

- [`http/usuarios.http`](http/usuarios.http)
- [`http/catalogo.http`](http/catalogo.http)
- [`http/pedidos.http`](http/pedidos.http): login → carrinho → checkout (202) → reenviar o GET
  até `PAGO` (caminho feliz); o caminho sem estoque (202 → `CANCELADO` com o motivo); e os erros
  (carrinho vazio, pedido de outra pessoa, cancelar pedido pago, catálogo fora)
- [`http/fluxo-compra.http`](http/fluxo-compra.http): a saga. Compra aprovada, compra com cartão
  final 0002 (o estoque volta) e compra cancelada no meio da cobrança (estorno), cada uma
  consultando o pedido, o pagamento e o estoque no fim; e quem pode ver qual pagamento
- [`http/demos.http`](http/demos.http): resiliência. Chama o banco-falso direto
  (`localhost:9000/config`): 30% de falha (o retry resolve), 100% (o circuito abre e fecha sozinho),
  3 s de latência (timeout e estorno de segurança) e a volta ao normal

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
| POST | `/pedidos/{id}/cancelar` | logado | 200 cancelado (em `AGUARDANDO_ESTOQUE` ou `AGUARDANDO_PAGAMENTO`), publica `pedido.cancelado` · 404 · 409 status não permite (ex.: `PAGO`) · 503 Kafka fora (não cancela) |
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
os 4 últimos dígitos. Cartão terminado em `0002` é recusado pelo banco-falso (saldo insuficiente).

### pagamentos

O pagamento nasce e muda por eventos; pela API, só se consulta. O Kong deixa passar qualquer perfil
logado e o serviço confere dono e admin (`X-User-Id`, `X-User-Role`).

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| GET | `/pagamentos/{pedidoId}` | logado | 200 do dono ou de admin · 404 não existe ou é de outra pessoa |
| GET | `/pagamentos` | logado (admin no serviço) | 200 todos · 403 perfil cliente |
| GET | `/health/pagamentos` | público | 200 `{ status, banco, circuitoBancoExterno }` (`fechado`, `aberto` ou `meio-aberto`) · 503 banco de dados fora |

Resposta: `{ id, pedidoId, usuarioId, valor, forma, cartaoFinal, status, tentativas,
codigoTransacao, motivo, criadoEm, atualizadoEm }`. Status: `PENDENTE` (gravado antes de chamar o
banco), `APROVADO`, `RECUSADO` (o banco disse não), `ERRO` (o banco não respondeu: timeout, 503,
circuito aberto), `CANCELADO` (pedido cancelado antes da cobrança), `ESTORNADO` (cobrado e
devolvido). `tentativas` e `motivo` respondem "por que meu pedido foi cancelado?" sem abrir log.

### banco-falso (direto, `http://localhost:9000`, fora do Kong)

| Método | Rota | Retornos |
|---|---|---|
| POST | `/cobrancas` `{ valor, cartaoFinal, referencia, forma }` | 201 `{ aprovado: true, codigo: "TX-..." }` · 402 `{ motivo: "saldo insuficiente" }` (cartão final 0002) · 503 (sorteio de `falhaPercent`) · 400 |
| POST | `/estornos` `{ codigo, referencia }` | 201 estornado (repetir dá 201 igual) · 404 sem cobrança aprovada |
| GET | `/config` | 200 `{ latenciaMs, falhaPercent }` |
| POST | `/config` `{ latenciaMs?, falhaPercent? }` | 200 (muda só o que vier) · 400 fora de 0–60000 / 0–100 |

A `referencia` é o id do pedido e funciona como chave de idempotência: a mesma referência devolve a
mesma resposta, sem cobrar de novo. A cobrança é registrada **antes** de esperar a latência: com
`latenciaMs` acima do timeout de quem chama, o banco cobra e a resposta não chega a tempo. Sem banco
de dados: reiniciou, esqueceu tudo.

---

## Eventos

Coreografia: ninguém manda. Cada serviço reage aos eventos que lhe interessam e publica os seus.

```
POST /pedidos → 202                        POST /pedidos/{id}/cancelar → 200
        │                                                 │
     pedidos ──pedido.criado · pedido.cancelado──► [tópico pedidos]
        ▲                                                 │
        │                         catalogo: reserva (criado) · devolve (cancelado)
        │                         pagamentos: estorna ou bloqueia (cancelado)
        │                                                 │
        │                     estoque.reservado / estoque.insuficiente
        │                                                 ▼
        ├────────────────────────────────────── [tópico estoque] ──► pagamentos ──HTTP──► banco-falso :9000
        │                                                               │  timeout · retry · circuit breaker
        │                                pagamento.aprovado / recusado / estornado
        │                                                               ▼
        └─────────────────────────────────────────────────────── [tópico pagamentos]
     muda o status                                                      │
                                                  catalogo: confirma (aprovado) · devolve (recusado)

     notificacoes escuta os três tópicos ┄┄► e-mail (Mailpit)
```

1. **Checkout**: o pedidos grava o pedido em `AGUARDANDO_ESTOQUE`, publica `pedido.criado` (tópico
   `pedidos`) e responde 202.
2. **Catálogo**: reserva tudo ou nada e publica `estoque.reservado` ou `estoque.insuficiente`
   (tópico `estoque`).
3. **Pagamentos**: escuta o tópico `estoque` (só cobra o que já tem estoque reservado), grava
   `PENDENTE`, cobra no banco e publica `pagamento.aprovado` ou `pagamento.recusado` (tópico
   `pagamentos`).
4. **Pedidos**: escuta `estoque` e `pagamentos` e muda o status: `AGUARDANDO_PAGAMENTO`, `PAGO` ou
   `CANCELADO` com o motivo.
5. **Catálogo**, de novo: `pagamento.aprovado` confirma a reserva (sai do reservado);
   `pagamento.recusado` e `pedido.cancelado` devolvem o estoque (a compensação).
6. **Notificações**: escuta os três tópicos e avisa o cliente.

| Evento | Tópico | Publica | Consome | dados |
|---|---|---|---|---|
| `pedido.criado` | `pedidos` | pedidos | catalogo, notificacoes | `usuarioId`, `clienteEmail`, `itens` (`produtoId`, `nome`, `precoUnitario`, `quantidade`), `valorTotal`, `formaPagamento`, `cartaoFinal` (só cartão) |
| `pedido.cancelado` | `pedidos` | pedidos (cliente cancelou) | catalogo (devolve o estoque), pagamentos (estorna ou bloqueia) | `usuarioId`, `clienteEmail`, `valorTotal`, `formaPagamento`, `cartaoFinal`, `motivo` |
| `estoque.reservado` | `estoque` | catalogo | pedidos, pagamentos | `usuarioId`, `clienteEmail`, `valorTotal`, `formaPagamento`, `cartaoFinal` (só cartão) |
| `estoque.insuficiente` | `estoque` | catalogo | pedidos, notificacoes | `motivo`, `usuarioId`, `clienteEmail` |
| `pagamento.aprovado` | `pagamentos` | pagamentos | pedidos (`PAGO`), catalogo (confirma), notificacoes | `usuarioId`, `clienteEmail`, `valor`, `codigoTransacao`, `tentativas` |
| `pagamento.recusado` | `pagamentos` | pagamentos | pedidos (`CANCELADO`), catalogo (devolve), notificacoes | `usuarioId`, `clienteEmail`, `valor`, `motivo`, `tentativas` |
| `pagamento.estornado` | `pagamentos` | pagamentos | notificacoes | `usuarioId`, `clienteEmail`, `valor`, `codigoTransacao` |

Recusa do banco e banco fora do ar dão no mesmo evento, `pagamento.recusado`, com motivos diferentes
(`saldo insuficiente` ou `banco indisponível (...)`). A saga compensa igual.

E-mails (Mailpit): "Recebemos o seu pedido", "Seu pedido foi cancelado: sem estoque", "Pagamento
aprovado: pedido confirmado", "Seu pedido foi cancelado: pagamento recusado" (com o motivo) e "Seu
pagamento foi estornado".

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
- **Group ids**: `pedidos`, `catalogo`, `pagamentos`, `notificacoes`, um por serviço. Réplicas do mesmo serviço
  dividem as partições; grupos diferentes recebem tudo, cada um no seu ritmo.
- **Evento é contrato**: só acrescente campos. Quem lê ignora o que não conhece
  (`@JsonIgnoreProperties(ignoreUnknown = true)`). Precisa quebrar? Publique um tipo novo
  (`pedido.criado.v2`) por um tempo, junto com o antigo.
- **Nunca no evento**: número do cartão, senha, documento. O evento fica guardado no tópico e muita
  gente lê.

### O status do pedido

```
                 estoque.reservado                       pagamento.aprovado
AGUARDANDO_ESTOQUE ───────────────► AGUARDANDO_PAGAMENTO ─────────────────► PAGO
  (nasce no checkout)                        │
        │ estoque.insuficiente               │ pagamento.recusado · cliente cancela
        │ pagamento.recusado · cliente       │
        └──────────────────────────► CANCELADO (motivoCancelamento) ◄──────┘
```

Cada seta só vale a partir do estado de origem (`UPDATE ... WHERE id = ? AND status IN (...)`): um
evento repetido ou atrasado não leva o pedido para trás. O status funciona como cadeado:
`AGUARDANDO_*` diz "isto ainda pode mudar", e um `CANCELADO` não volta a `PAGO` porque o banco
aprovou atrasado. `POST /pedidos/{id}/cancelar` é permitido em `AGUARDANDO_ESTOQUE` e
`AGUARDANDO_PAGAMENTO`; depois de `PAGO`, 409. O `pagamento.aprovado` também vale a partir de
`AGUARDANDO_ESTOQUE` (ADR-022).

---

## A saga da compra

Uma compra atravessa três bancos (Postgres do pedidos, Mongo do catálogo, Postgres do pagamentos):
não existe um `ROLLBACK` que desfaça os três. Cada passo confirma a sua parte na hora e, se algo der
errado depois, é desfeito por uma **compensação**: uma ação nova, que também vira evento.

| Passo (transação local) | Serviço | Compensação | Disparada por |
|---|---|---|---|
| Criar o pedido | pedidos | marcar `CANCELADO` com o motivo | `estoque.insuficiente`, `pagamento.recusado` |
| Reservar o estoque | catalogo | devolver ao disponível | `pagamento.recusado`, `pedido.cancelado` |
| Cobrar o cartão | pagamentos | estornar (ou gravar `CANCELADO` antes de cobrar) | `pedido.cancelado` |
| Avisar o cliente | notificacoes | mandar outro e-mail | `pagamento.recusado`, `pagamento.estornado` |

- **A ordem importa**: o estoque é reservado antes de cobrar porque devolver estoque é um `UPDATE` no
  nosso banco, de graça; estornar custa taxa e aparece no extrato. Sem estoque, o banco nem é chamado.
- **Cada serviço conhece só a própria compensação**: o catálogo não sabe por que o pagamento falhou,
  só escuta `pagamento.recusado` e devolve.
- **Compensação idempotente**: o catálogo só devolve se a reserva ainda estiver `RESERVADO` (ou
  `CONFIRMADO`, no cancelamento durante a cobrança), num `findAndModify` com o status no filtro;
  devolver duas vezes não acontece. O pagamentos só estorna o que está `APROVADO`.
- **Cancelado no meio da cobrança**: o `pedido_id UNIQUE` resolve os dois casos. Se o
  `pedido.cancelado` chega antes da cobrança, o pagamentos grava `CANCELADO` e o `estoque.reservado`
  que vier depois é ignorado (o banco nem é chamado). Se chega depois de aprovado, estorna e publica
  `pagamento.estornado`. Os dois finais estão certos: ninguém paga por um pedido cancelado.

## Resiliência: a chamada ao banco

Só o pagamentos chama um sistema externo, e é ali que ficam as defesas
([`CobrancaResiliente`](payment-api/src/main/java/br/com/ecommerce/pagamentos/banco/CobrancaResiliente.java)):

| Padrão | Configuração | Por quê |
|---|---|---|
| **Timeout** | 2 s por chamada (`banco.timeout`) | sem timeout, o `HttpURLConnection` espera para sempre e prende a thread |
| **Retry** | até 3 tentativas, esperando 500 ms e 1 s, mais jitter; só em timeout, sem conexão, 5xx e 429 | 402 e outros 4xx são respostas: repetir não muda o "não" |
| **Idempotência** | a `referencia` (id do pedido) em toda tentativa | o retry de uma cobrança que o banco já fez devolve a mesma, sem cobrar duas vezes |
| **Circuit breaker** | Resilience4j: abre com 50% de falha em pelo menos 5 chamadas (janela das últimas 10), espera 15 s, meio-aberto deixa 1 chamada testar | quem chama descobre na hora, e o banco doente ganha um respiro |
| **Fallback** | esgotou ou circuito aberto: `ERRO` e `pagamento.recusado` com "banco indisponível (...)" | o pedido não fica parado esperando um banco que caiu: a saga compensa |
| **Estorno de segurança** | se alguma tentativa deu timeout e o resultado foi `ERRO`: estorno pela referência | timeout é "não sei": o banco pode ter cobrado. Sem cobrança, o banco responde 404 e tudo bem |

Para o circuito, falha é só `BancoIndisponivelException` (timeout, sem conexão, 5xx). Um 402 é o
banco respondendo: sucesso. O estorno não passa pelo circuit breaker (devolver dinheiro é importante
demais para ser barrado) e tem timeout maior (10 s). O retry fica só nesta camada, a mais próxima de
quem falha; o pedidos → catálogo continua sem retry (o cliente está esperando o checkout).

### Como demonstrar o circuit breaker

Com o [`http/demos.http`](http/demos.http), ou com curl:

```bash
docker compose logs -f pagamentos banco-falso          # num terminal
curl -X POST localhost:9000/config -H 'Content-Type: application/json' -d '{"falhaPercent":100}'
# faça 2 ou 3 compras (pix): cada uma aparece com "tentativa N/3 falhou: HTTP 503"
# no log: "CIRCUITO ABERTO"; a próxima compra nem chama o banco ("circuito aberto, cobrança não tentada")
curl localhost:8080/health/pagamentos                  # "circuitoBancoExterno": "aberto"
curl -X POST localhost:9000/config -H 'Content-Type: application/json' -d '{"falhaPercent":0}'
# espere 15 s: "CIRCUITO MEIO-ABERTO" e /health "meio-aberto"
# faça mais uma compra: a chamada de teste passa, "CIRCUITO FECHADO" e /health "fechado"
```

Os pedidos que pegaram o circuito aberto ficam `CANCELADO` com "Pagamento recusado: banco
indisponível (circuito aberto).", o estoque volta e o cliente recebe o e-mail com o motivo.

### Limitações conhecidas

| Problema | O que acontece hoje | Como se resolve |
|---|---|---|
| **Escrita dupla** | O pedido é gravado e o Kafka cai antes do `pedido.criado`: o pedido fica parado em `AGUARDANDO_ESTOQUE` para sempre (há um `ERROR` no log com o id). No pagamentos é pior: o pagamento fica gravado `APROVADO` e, se o Kafka não voltar dentro das 3 tentativas do tratador de erros (cerca de 30 s), o `pagamento.aprovado` se perde. O cliente foi cobrado, o pedido fica em `AGUARDANDO_PAGAMENTO` e o estoque, reservado (aconteceu de verdade num teste com o WSL travado). | Padrão **Outbox**: gravar o evento numa tabela na mesma transação do pedido; um publicador lê a tabela e publica até conseguir. |
| **Mensagem venenosa** | Um evento que dá erro sempre é tentado 3 vezes (1 s entre elas) e depois **pulado**; só fica o erro no log. | Mandar para um tópico de erros (**DLQ**) para analisar e reprocessar. |
| **Reserva pela metade** | Se o catálogo cair no meio da reserva de um pedido, a reserva fica `PROCESSANDO`; quando o evento chega de novo, é ignorado (não dá para saber o que já saiu do estoque). | Transação no Mongo envolvendo a reserva e os produtos, ou Outbox também no catálogo. |
| **Estorno que falha** | O banco não responde ao estorno: o Kafka entrega o `pedido.cancelado` mais 2 vezes (cada uma com 3 tentativas) e desiste; fica um `ERROR` no log e o pagamento continua `APROVADO`. O cliente pagou por um pedido cancelado e nenhuma tabela mostra isso. | Status `ESTORNO_PENDENTE`, uma rotina que reprocessa com espera crescente, alerta para o financeiro (DLQ) e a conciliação diária com o banco. |
| **Compensação pela metade** | Se o catálogo cair entre marcar a reserva `LIBERADO` e devolver cada item, o evento que chegar de novo encontra a reserva já liberada e não devolve o resto. | Transação no Mongo envolvendo a reserva e os produtos. |
| **Reserva sem validade** | Se o pagamento nunca terminar (pagamentos fora por horas), o estoque fica preso em `reservado`. | Reservas com prazo: sem confirmação em X minutos, expiram e o estoque volta. |
| **Um circuito por réplica** | O estado do circuit breaker fica na memória de cada réplica do pagamentos: com 3 réplicas, o banco recebe até 3 × 5 chamadas ruins antes de todas abrirem, e por alguns segundos elas discordam. | Para o projeto, aceitável. Estado compartilhado (Redis) ou o circuito na infraestrutura (Envoy, Istio). |
| **Banco caiu = venda perdida** | Banco fora: o pedido é cancelado na hora (fallback mais simples). | Deixar o pedido esperando: pausar o consumo com o circuito aberto, ou uma rotina que cobra os `PENDENTE`, sempre com prazo. Decisão de negócio. |
| **Banco-falso sem disco** | Reiniciou o banco-falso, esqueceu as cobranças: um estorno depois disso dá 404. | É uma simulação; um banco de verdade guarda tudo. |
| **E-mail repetido** | Pelo menos uma vez: se o mesmo evento for entregue de novo, sai outro e-mail. | Guardar os `id` de evento já tratados. Para um aviso, é aceitável. |
| **Kafka sem disco próprio e com um broker só** | `docker compose down` apaga os tópicos e os offsets; um broker caído para a mensageria. | Volume para os dados e 3 brokers com fator de replicação 3 (produção). |

### Formato de erro

Os serviços respondem no mesmo formato:

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
│       ├── evento/           # Eventos (envelope), PedidosListener, PagamentosListener, dados do estoque.*
│       ├── exception/  seed/
├── order-api/                # serviço pedidos
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/pedidos/
│       ├── entity/  repository/  dto/  service/  controller/
│       ├── evento/           # Eventos (envelope), EstoqueListener, PagamentosListener, pedido.criado/cancelado
│       ├── catalogo/  security/  exception/
├── payment-api/              # serviço pagamentos
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/
│       ├── resources/schema.sql   # a tabela pagamentos (CREATE TABLE IF NOT EXISTS)
│       └── java/br/com/ecommerce/pagamentos/
│           ├── banco/        # BancoClient (HTTP), CobrancaResiliente (retry), BancoConfig (circuit breaker)
│           ├── evento/       # Eventos (envelope), PagamentosListener, dados dos pagamento.*
│           ├── entity/  repository/  dto/  service/  controller/
│           └── config/  security/  exception/
├── notification-api/         # serviço notificacoes (sem banco, sem porta)
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/notificacoes/
│       ├── evento/           # Eventos (envelope), NotificacoesListener
│       └── email/            # EmailService (SMTP → Mailpit)
└── fake-bank/                # banco-falso (sem banco de dados, porta 9000)
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/br/com/ecommerce/banco/BancoController.java
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
algo falha no meio é a saga (ADR-018, aula 4).

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

**Consequências.** Reservado não é vendido: o pagamento confirma a venda ou devolve ao disponível
(ADR-018). O `PATCH /produtos/{id}/estoque` continua definindo o disponível e não mexe no
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

### ADR-018 — Saga coreografada, com uma compensação por serviço

**Contexto.** A compra grava em três bancos de três serviços; não há transação que desfaça os três.
O commit em duas fases (2PC) exigiria todos no ar ao mesmo tempo e travaria registros, e o Kafka, o
Mongo e o banco do cartão não participam dele.

**Decisão.** Saga em **coreografia**, continuando o ADR-014: cada passo é uma transação local e
cada serviço conhece só a própria compensação (seção [A saga da compra](#a-saga-da-compra)). Nada
de orquestrador: o fluxo tem poucos passos e o projeto já é coreografado. As compensações são
idempotentes por filtro de estado: a reserva ganhou os status `CONFIRMADO` e `LIBERADO`, mudados com
`findAndModify` e o status de origem no filtro; quem recebe a reserva de volta é o único que mexe no
estoque. O cancelamento depois de confirmado (o cliente cancelou enquanto o banco aprovava) também
devolve o estoque, porque o pagamentos estorna essa cobrança. A ordem dos passos segue o custo de
desfazer: reservar (barato) antes de cobrar (estorno custa taxa); e-mail de confirmação só depois do
pagamento aprovado.

**Consequências.** Sem isolamento: por alguns segundos, outro cliente vê "esgotado" por causa de uma
reserva que vai ser devolvida. O fluxo inteiro só aparece somando os logs (o `correlationId` segue
de evento em evento). Uma compensação que falha de vez fica só no log (limitação conhecida).

### ADR-019 — Resiliência só na chamada ao banco: timeout, retry local e Resilience4j

**Contexto.** Os slides usam `fetch` com `AbortSignal.timeout` e o opossum (Node). O banco do cartão
é o único sistema externo, lento e instável por definição.

**Decisão.** No pagamentos: timeout de 2 s (`SimpleClientHttpRequestFactory`), retry escrito à mão
(3 tentativas, backoff de 500 ms e 1 s com jitter, só para falhas passageiras, com log de cada uma)
e o **Resilience4j** como circuit breaker. Só o núcleo (`resilience4j-circuitbreaker`), sem a
integração Spring, usado pela API de baixo nível (`tryAcquirePermission`, `onSuccess`, `onError`)
dentro do laço de retry: com o circuito aberto, nem a primeira tentativa acontece. Configuração
equivalente à do opossum do slide (`failureRateThreshold 50`, `minimumNumberOfCalls 5`,
`waitDurationInOpenState 15s`), com a transição automática para meio-aberto para o `/health`
mostrar. Retry só aqui: o Kong já faz retry de conexão e o pedidos → catálogo tem o cliente
esperando.

**Consequências.** Pior caso de uma cobrança: 3 × 2 s + 1,5 s de espera, numa thread do listener
(longe do `max.poll.interval` de 5 min). O estado do circuito é por réplica.

### ADR-020 — Pagamentos com JDBC e `schema.sql`, sem JPA

**Decisão.** A idempotência está no SQL: `INSERT ... ON CONFLICT DO NOTHING` com `pedido_id UNIQUE`,
e toda mudança de status com o status de origem no `WHERE` (`concluir` só de `PENDENTE`,
`marcarEstornado` só de `APROVADO` ou `CANCELADO`). Com JPA, isso viraria consultas nativas de
qualquer jeito, então o serviço usa `JdbcTemplate`, e a tabela vem de um
[`schema.sql`](payment-api/src/main/resources/schema.sql) com `CREATE TABLE IF NOT EXISTS`, igual à
do slide (o UUID é gerado pela aplicação, como no ADR-002). O mesmo SQL roda no H2 dos testes, em
modo PostgreSQL.

**Consequências.** O SQL fica à vista e é o mesmo do slide. Mudança de esquema exige mexer no
`schema.sql` à mão (ou `docker compose down -v`); se crescer, Flyway.

### ADR-021 — Cancelar publica o `pedido.cancelado` antes do commit

**Contexto.** No checkout, o evento sai depois do commit (ADR-015): melhor um pedido parado do que um
evento de um pedido que sumiu. No cancelamento, a conta é outra: um pedido `CANCELADO` sem o evento
deixaria o estoque preso e a cobrança seguindo, e o cliente pagaria por um pedido cancelado.

**Decisão.** O `UPDATE` condicional e o `publicar` (que espera o Kafka confirmar) ficam na mesma
transação. Kafka fora: rollback e **503**, o pedido continua como estava e o cliente tenta de novo.

**Consequências.** Se o commit falhar depois de o evento sair (raro), o catálogo devolve o estoque e
o pagamentos bloqueia a cobrança, mas o pedido continua `AGUARDANDO_*`. Resolver de vez é o Outbox.

### ADR-022 — `PAGO` também a partir de `AGUARDANDO_ESTOQUE`

**Contexto.** O slide pede `PAGO` só a partir de `AGUARDANDO_PAGAMENTO`. Mas o pedidos e o
pagamentos reagem ao **mesmo** `estoque.reservado`, e o `pagamento.aprovado` vem por outro tópico,
sem ordem garantida em relação a ele. Se o pagamento terminar antes de o pedidos tratar o
`estoque.reservado` (pedidos reiniciando, com lag), o aprovado seria ignorado e o pedido ficaria em
`AGUARDANDO_PAGAMENTO` para sempre, pago.

**Decisão.** `pagamento.aprovado` leva a `PAGO` a partir de `AGUARDANDO_ESTOQUE` ou
`AGUARDANDO_PAGAMENTO` (o `pagamento.recusado` já aceitava os dois, como no slide). Um aprovado só
existe se o estoque foi reservado, então o salto é seguro; o `estoque.reservado` que chegar depois é
ignorado pelo filtro de estado. O que importa continua valendo: `CANCELADO` nunca vira `PAGO`.

**Consequências.** Nenhum pedido pago fica preso por causa de uma corrida entre tópicos.

### ADR-023 — Pagamentos ouve os dois tópicos num listener só

**Decisão.** Um `@KafkaListener` para `estoque` e `pedidos`, com o `RangeAssignor` (o padrão,
escrito na configuração): a partição N dos dois tópicos vai para a mesma réplica e os eventos de um
pedido (mesma chave) são tratados numa thread só. A cobrança e o cancelamento do mesmo pedido não
rodam ao mesmo tempo. Para o caso raro de troca de réplica no meio, o resultado da cobrança só é
gravado se o pagamento ainda estiver `PENDENTE`; se o cancelamento chegou antes, o aprovado é
estornado na hora. Um `PENDENTE` deixado por uma queda é retomado quando o evento chega de novo:
seguro, porque o banco reconhece a referência.

**Consequências.** Depende de os dois tópicos terem o mesmo número de partições (3).

---

## Para discutir em dupla (aula 4: saga e resiliência)

**1. O banco caiu e os pedidos foram cancelados. Como seria deixá-los esperando?** O pagamento
ficaria `PENDENTE` (ou um `AGUARDANDO_BANCO`), o pedido continuaria `AGUARDANDO_PAGAMENTO` ("em
processamento") e o estoque continuaria reservado. Quem tenta de novo: pausar o consumo com o
circuito aberto (o container do `@KafkaListener` tem `pause()`), e os eventos esperam no Kafka; ou
uma rotina que cobra os `PENDENTE` de tempos em tempos. Sempre com prazo: passadas, por exemplo,
2 horas, publica `pagamento.recusado` e a saga compensa. Não perde a venda, mas prende estoque,
deixa o cliente sem resposta e dá mais trabalho. Decisão de negócio; hoje cancelamos.

**2. O estorno também falhou. Onde fica registrado?** Hoje, só no log: o pagamento continua
`APROVADO` e o pedido, `CANCELADO` (ver [Limitações](#limitações-conhecidas)). O certo: gravar
`ESTORNO_PENDENTE`, uma rotina que tenta de novo com espera crescente (o estorno pela referência é
idempotente), alerta para o financeiro e um tópico de erros, e-mail "estorno em processamento", e a
conciliação diária pegando o que escapou. Compensação que falha não pode sumir.

**3. Por que reservar antes de cobrar?** Desfazer uma reserva é um `UPDATE` no nosso banco, de graça
e invisível ao cliente; desfazer uma cobrança é um estorno no banco de outra empresa, com taxa, dias
de prazo e duas linhas no extrato. Cobrando primeiro, todo pedido sem estoque viraria um estorno;
reservando primeiro, ele nem chega ao banco. O preço: o estoque fica preso enquanto o pagamento
demora (reservas deveriam ter validade).

**4. Pagamentos com 3 réplicas, cada uma com o seu circuit breaker. É um problema?** Na maioria das
vezes, não. Cada réplica precisa das próprias falhas para abrir (o banco recebe até 15 chamadas
ruins, e não 5) e por alguns segundos elas discordam, mas cada uma para de insistir e o banco
recebe muito menos. Com o Kafka, cada réplica já cuida só das suas partições. Estado único: Redis
ou service mesh. Para o projeto, um por réplica basta.

**5. Onde colocar o retry?** No pagamentos → banco, sim (3 tentativas): é externo, as falhas são
passageiras, ninguém está esperando (é por evento) e é idempotente pela referência. No pedidos →
catálogo, no máximo 1 tentativa curta: o cliente está esperando o checkout, e 3 tentativas de 3 s
estourariam o tempo dele; melhor 503 rápido (é o que fazemos). Nos dois sem pensar, não: as
tentativas se multiplicam a cada camada. E o Kong já faz retry de conexão (5 por padrão), uma
camada que ninguém configurou: para `POST /pedidos`, que não é idempotente, vale olhar esse número.


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
estoque?** Na aula 3, ninguém: o pedido ficava `CANCELADO` e as unidades continuavam em `reservado`.
Desde a aula 4, o pedidos publica `pedido.cancelado` e o catálogo, ao receber, devolve a reserva
daquele pedido ao disponível: desfazer um passo anterior com outro evento é a saga (ADR-018).

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
