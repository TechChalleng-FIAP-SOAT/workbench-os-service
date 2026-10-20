-- =====================================================================
-- V7 — Robustez da Saga: retry/timeout de etapa.
--
-- O orquestrador passa a rastrear o comando pendente (enviado a um participante
-- que aguarda resposta), o número de tentativas e o instante do último envio.
-- Isso permite reenviar o comando após um timeout (comando/evento perdido) e,
-- esgotadas as tentativas, acionar a compensação (ex.: liberar reserva quando a
-- geração de orçamento falha após a reserva — ADR-0005).
-- =====================================================================

ALTER TABLE saga_state ADD COLUMN pending_command  VARCHAR(100);
ALTER TABLE saga_state ADD COLUMN pending_payload  VARCHAR(4000);
ALTER TABLE saga_state ADD COLUMN retry_count      INTEGER NOT NULL DEFAULT 0;
ALTER TABLE saga_state ADD COLUMN last_command_at  TIMESTAMP;
