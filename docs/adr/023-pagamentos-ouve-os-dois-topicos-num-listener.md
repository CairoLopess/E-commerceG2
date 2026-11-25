# ADR-023 — Pagamentos ouve os dois tópicos num listener só

**Decisão.** Um `@KafkaListener` para `estoque` e `pedidos`, com o `RangeAssignor` (o padrão,
escrito na configuração): a partição N dos dois tópicos vai para a mesma réplica e os eventos de um
pedido (mesma chave) são tratados numa thread só. A cobrança e o cancelamento do mesmo pedido não
rodam ao mesmo tempo. Para o caso raro de troca de réplica no meio, o resultado da cobrança só é
gravado se o pagamento ainda estiver `PENDENTE`; se o cancelamento chegou antes, o aprovado é
estornado na hora. Um `PENDENTE` deixado por uma queda é retomado quando o evento chega de novo:
seguro, porque o banco reconhece a referência.

**Consequências.** Depende de os dois tópicos terem o mesmo número de partições (3).
