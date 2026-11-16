# ADR-019 — Resiliência só na chamada ao banco: timeout, retry local e Resilience4j

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
