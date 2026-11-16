# ADR-010 — Todo serviço escuta na porta 3000 dentro da rede

**Decisão.** No compose, `PORT=3000` em todos os serviços, que se chamam pelo nome
(`http://catalogo:3000`). Sem `ports:` publicadas, cada container tem a sua rede e não há
conflito. Fora do Docker, os padrões continuam 3001, 3002 e 3003, para os três rodarem juntos na
mesma máquina.

**Consequências.** Uma convenção só, igual ao `kong.yml` de referência da aula. Serviço novo não
precisa de porta nova.
