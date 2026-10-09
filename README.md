# E-commerce G2 — microserviços

Projeto da disciplina de Arquitetura de Sistemas: um e-commerce em microserviços, construído
uma peça por aula. **Parte 1 (esta entrega): usuários e catálogo**, cada serviço com o seu banco,
subindo juntos com um comando.

| Serviço | Pasta | Porta | Banco | Responsabilidade |
|---|---|---|---|---|
| usuarios | [`usersApi/`](usersApi) | 3001 | Postgres | cadastro, login (JWT) e endereços |
| catalogo | [`catalog-api/`](catalog-api) | 3002 | Mongo | produtos, categorias e estoque |

Stack: Java 21, Spring Boot 4.1, Spring Data JPA / Spring Data MongoDB, Spring Security
(OAuth2 Resource Server para o JWT), Docker Compose.

---

## Como subir

Pré-requisito: Docker com o Compose.

```bash
cp .env.example .env      # e troque o JWT_SECRET (mínimo 32 caracteres)
docker compose up --build
```

Pronto quando estes dois respondem `200`:

- http://localhost:3001/health
- http://localhost:3002/health

Para zerar os bancos (apaga os dados e roda o seed de novo): `docker compose down -v`.

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

O banco `usuarios` precisa existir no Postgres (`CREATE DATABASE usuarios;`); as tabelas são
criadas na subida.

### Testes automatizados

```bash
cd usersApi && ./mvnw test      # H2 em memória, não precisa de banco
cd catalog-api && ./mvnw test   # Mongo embutido (baixado na 1ª execução), não precisa de Docker
```

---

## Testando a API: arquivos `.http`

Em [`http/`](http), um arquivo por serviço, com todas as rotas e os casos de erro (400, 401, 403,
404, 409). No VS Code, instale a extensão **REST Client** e use o botão **Send Request**.

- [`http/usuarios.http`](http/usuarios.http)
- [`http/catalogo.http`](http/catalogo.http)

Rode de cima para baixo: as requisições marcadas com `# @name` guardam a resposta (token, ids)
para as seguintes.

---

## Rotas

### usuarios (`:3001`)

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| POST | `/auth/login` | público | 200 `{ token, usuario }` · 401 |
| POST | `/usuarios` | público | 201 · 400 · 409 e-mail já existe |
| GET | `/usuarios/me` | logado | 200 · 401 |
| PUT | `/usuarios/me` | logado | 200 · 400 · 409 |
| GET | `/usuarios/me/enderecos` | logado | 200 |
| POST | `/usuarios/me/enderecos` | logado | 201 · 400 |
| DELETE | `/usuarios/me/enderecos/{id}` | logado | 204 · 404 não existe ou não é meu |
| GET | `/usuarios` | admin | 200 · 401 · 403 |
| GET | `/health` | público | 200 · 503 |

### catalogo (`:3002`)

| Método | Rota | Acesso | Retornos |
|---|---|---|---|
| GET | `/produtos?categoria=&busca=` | público | 200 (só ativos) |
| GET | `/produtos/{id}` | público | 200 · 404 |
| POST | `/produtos` | admin* | 201 · 400 · 409 SKU já existe |
| PUT | `/produtos/{id}` | admin* | 200 · 400 · 404 · 409 |
| PATCH | `/produtos/{id}/estoque` | admin* | 200 · 400 estoque negativo · 404 |
| DELETE | `/produtos/{id}` | admin* | 204 desativado · 404 |
| GET | `/categorias` | público | 200 |
| POST | `/categorias` | admin* | 201 · 400 · 409 slug já existe |
| GET | `/health` | público | 200 · 503 |

\* Abertas nesta aula (ver ADR-004).

### Formato de erro (os dois serviços)

```json
{ "erro": "Dados inválidos.", "detalhes": ["preco deve ser maior que zero", "sku é obrigatório"] }
```

Nunca sai stack trace, mensagem do banco ou `senha_hash`.

---

## Estrutura

```
.
├── docker-compose.yml        # sobe os 2 serviços e os 2 bancos
├── .env.example              # variáveis (copie para .env)
├── http/                     # requisições de teste (REST Client)
├── usersApi/                 # serviço usuarios
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/br/com/ecommerce/usuarios/
│       ├── entity/  repository/  dto/  service/  controller/
│       ├── config/  security/  exception/  seed/
└── catalog-api/              # serviço catalogo
    ├── Dockerfile
    ├── pom.xml
    └── src/main/java/br/com/ecommerce/catalogo/
        ├── document/  repository/  dto/  service/  controller/
        ├── exception/  seed/
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
