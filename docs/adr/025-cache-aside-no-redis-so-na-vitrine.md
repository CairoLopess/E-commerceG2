# ADR-025 — Cache-aside no Redis, só na vitrine, invalidado por versão

**Contexto.** A vitrine (produtos e categorias) é o que mais se lê e o que menos muda. Cada filtro
(`categoria`, `busca`) é uma resposta diferente, e o estoque que ela mostra muda a cada compra.

**Decisão.** Redis no compose, sem persistência (`--save ""`, sem AOF), com `maxmemory 64mb` e
`volatile-lru`. O catálogo faz cache-aside de `GET /produtos`, `/produtos/{id}` e `/categorias`:
guarda o JSON inteiro da resposta, com TTL de 60 s (mais até 10% de variação, contra avalanche), e
responde `X-Cache: HIT`, `MISS` ou `OFF`. A invalidação é por **versão**: o nome da chave leva
`catalogo:versao`, e toda escrita de produto, categoria ou estoque (inclusive a reserva, a
confirmação e a devolução por evento) faz um `INCR`; as chaves velhas só deixam de ser lidas e
vencem pelo TTL. O `GET /produtos?ids=` do checkout **não** usa o cache. Com o Redis fora, a vitrine
vai direto ao Mongo e pula o Redis por 5 s, e o `/health` fica `degradado`. O acesso ao Redis passa
por uma interface (`CacheStore`), e os testes usam um mapa em memória que pode ser desligado.

**Consequências.** Um comando invalida todas as listagens, sem precisar saber quais chaves existem;
o preço é invalidar demais: cada compra troca a versão e a vitrine inteira volta ao Mongo. Se o
Redis falhar no instante de uma escrita, a versão não muda e o dado velho pode aparecer por até
60 s; o checkout continua com o preço certo. Sem persistência, um Redis reiniciado volta vazio (mais
MISS logo depois), nunca com dado velho.
