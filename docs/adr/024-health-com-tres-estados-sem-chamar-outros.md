# ADR-024 — `/health` com três estados, sem chamar outros serviços

**Contexto.** Até a aula 4, cada `/health` respondia `{ status, banco }`, com 200 ou 503. O Docker
não olhava para ele, e "o processo respondeu" não dizia nada sobre o Kafka, o cache ou o banco do
cartão. Um serviço pode estar vivo e não conseguir trabalhar, ou trabalhar pior sem estar quebrado.

**Decisão.** Todos os serviços respondem `{ servico, status, dependencias }`, com `ok`, `degradado`
ou `fora do ar`. Só a falta de uma dependência **essencial** (o banco do serviço, o Kafka; o SMTP no
notificacoes) dá `fora do ar` e 503. O que só acelera (o Redis) ou está fora do nosso controle (o
banco do cartão, cujo circuito aberto já faz o pagamentos recusar rápido) dá `degradado` e 200. Cada
dependência é testada de verdade e barato: `SELECT 1`, `ping` no Mongo, `describeCluster` no Kafka
(com timeout de 2 s e um cliente de admin reaproveitado), `PING` no Redis, abrir a conexão SMTP.
Nenhum health chama o health de outro serviço. O notificacoes ganhou um servidor HTTP só para isso,
sem porta publicada e sem rota no Kong. O compose chama o `/health` de cada serviço a cada 10 s
(`wget`), e o gateway só sobe quando os serviços da API estão `healthy`. O `/health` não vai para o
log.

**Consequências.** Um só endpoint faz o papel de readiness (o Docker usa o 503 para marcar
`unhealthy`); não há um liveness separado (`/health/live`), que um orquestrador usaria para decidir
reiniciar. Se o Kafka cair, todos os serviços que o usam ficam `unhealthy` juntos: é verdade (nenhum
deles consegue trabalhar), mas um orquestrador que reiniciasse por readiness reiniciaria todos à toa.
O `depends_on` do gateway faz o `docker compose up -d` esperar a loja ficar pronta, e falhar se um
serviço não ficar.
