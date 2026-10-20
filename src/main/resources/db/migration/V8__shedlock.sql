-- =====================================================================
-- V8 — Tabela do ShedLock (lock distribuído para jobs @Scheduled).
-- Garante que, com múltiplas réplicas (HPA), apenas uma execute cada job por
-- janela — usado pelo reenvio/timeout da Saga e pelo lembrete de pagamento.
-- =====================================================================

CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until TIMESTAMP    NOT NULL,
    locked_at  TIMESTAMP    NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);
