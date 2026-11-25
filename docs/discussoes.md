# Para discutir em dupla

As perguntas do fim de cada aula, respondidas com o que o projeto faz. Da mais recente para a mais antiga.

## Aula 5: observabilidade e cache

**1. O `/health` do pedidos deveria chamar o `/health` do catálogo, já que depende dele?** Não. O
health diz se o próprio serviço consegue trabalhar. Se o pedidos ficasse "fora do ar" porque o
catálogo caiu, um orquestrador reiniciaria o pedidos à toa, e uma queda viraria várias (falha em
cascata); além disso, healths chamando healths multiplicam as chamadas a cada 10 s. A dependência
do catálogo aparece onde importa: no 503 do checkout e no `/health/catalogo`. No projeto, o
`/health/pedidos` testa só o próprio banco e o Kafka.

**2. Chegou um pedido enquanto o banco do estoque estava fora. O que aconteceu com ele?** No
projeto, o listener do catálogo lança a exceção, o offset não é confirmado e o tratador de erros
tenta de novo mais 2 vezes, 1 s entre elas. Se o Mongo voltou nesse meio-tempo, a reserva acontece
(o `pedidoId` único impede reservar duas vezes). Se não voltou, a mensagem é **pulada**: o pedido fica
em `AGUARDANDO_ESTOQUE` para sempre, e só sobra o erro no log. O certo é não pular erro passageiro
(espera crescente até o banco voltar) ou mandar para um tópico de erros (DLQ) e reprocessar depois:
é a "mensagem venenosa" das [limitações conhecidas](../README.md#limitações-conhecidas). Enquanto
isso, o `/health/catalogo` responde 503 e o Docker o marca `unhealthy`, então pelo menos dá para ver.

**3. O admin mudou o preço de um produto. Quanto tempo o cliente pode ver o preço antigo na
vitrine?** Nenhum, se a invalidação funcionar: o `PUT` faz `INCR catalogo:versao`, e a próxima
leitura já monta a chave da versão nova (MISS) e busca o preço novo. No máximo o TTL (60 s), se a
invalidação falhar (o Redis estava fora naquele instante e voltou sem reiniciar). E no checkout o
preço nunca vem da vitrine: o pedidos consulta o catálogo com `?ids=`, que não passa pelo cache.

**4. Vale cachear o `GET /pedidos/{id}`, que o cliente consulta várias vezes enquanto espera?**
Não. Ele consulta justamente para ver o status **mudar** (`AGUARDANDO_*` → `PAGO`); com cache,
veria o status velho e consultaria ainda mais. É um dado de uma pessoa só, lido poucas vezes, e o
Postgres do pedidos responde rápido. Cache vale para o que muitos leem e muda pouco; para avisar
que algo mudou, o caminho é avisar (o e-mail que já mandamos, ou um WebSocket), não guardar.

## Aula 4: saga e resiliência

**1. O banco caiu e os pedidos foram cancelados. Como seria deixá-los esperando?** O pagamento
ficaria `PENDENTE` (ou um `AGUARDANDO_BANCO`), o pedido continuaria `AGUARDANDO_PAGAMENTO` ("em
processamento") e o estoque continuaria reservado. Quem tenta de novo: pausar o consumo com o
circuito aberto (o container do `@KafkaListener` tem `pause()`), e os eventos esperam no Kafka; ou
uma rotina que cobra os `PENDENTE` de tempos em tempos. Sempre com prazo: passadas, por exemplo,
2 horas, publica `pagamento.recusado` e a saga compensa. Não perde a venda, mas prende estoque,
deixa o cliente sem resposta e dá mais trabalho. Decisão de negócio; hoje cancelamos.

**2. O estorno também falhou. Onde fica registrado?** Hoje, só no log: o pagamento continua
`APROVADO` e o pedido, `CANCELADO` (ver [Limitações](../README.md#limitações-conhecidas)). O certo: gravar
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


## Aula 3: Kafka e eventos

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
no log. É a escrita dupla; a solução é o Outbox (ver [Limitações](../README.md#limitações-conhecidas)).

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

## Aula 2: gateway e pedidos

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
