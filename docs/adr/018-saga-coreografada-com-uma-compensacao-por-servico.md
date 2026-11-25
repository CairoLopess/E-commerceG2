# ADR-018 — Saga coreografada, com uma compensação por serviço

**Contexto.** A compra grava em três bancos de três serviços; não há transação que desfaça os três.
O commit em duas fases (2PC) exigiria todos no ar ao mesmo tempo e travaria registros, e o Kafka, o
Mongo e o banco do cartão não participam dele.

**Decisão.** Saga em **coreografia**, continuando o ADR-014: cada passo é uma transação local e
cada serviço conhece só a própria compensação (seção [A saga da compra](../../README.md#a-saga-da-compra)). Nada
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
