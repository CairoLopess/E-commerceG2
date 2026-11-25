# ADR-001 — Persistência poliglota: Postgres no usuarios, Mongo no catalogo

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
