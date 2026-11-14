-- Roda a cada subida (spring.sql.init.mode=always): IF NOT EXISTS não apaga nada.
-- O mesmo SQL roda no Postgres e no H2 dos testes (modo PostgreSQL).
CREATE TABLE IF NOT EXISTS pagamentos (
  id               UUID PRIMARY KEY,
  pedido_id        UUID NOT NULL UNIQUE,      -- idempotência: um pagamento por pedido
  usuario_id       UUID NOT NULL,
  valor            NUMERIC(12,2) NOT NULL,
  forma            TEXT,
  cartao_final     TEXT,
  status           TEXT NOT NULL CHECK (status IN (
                     'PENDENTE', 'APROVADO', 'RECUSADO', 'ERRO', 'CANCELADO', 'ESTORNADO')),
  tentativas       INT NOT NULL DEFAULT 0,
  codigo_transacao TEXT,                      -- o TX-... do banco
  motivo           TEXT,
  criado_em        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  atualizado_em    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_pagamentos_usuario ON pagamentos (usuario_id);
