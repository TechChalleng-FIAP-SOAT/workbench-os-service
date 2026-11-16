-- =====================================================================
-- V9 — Concorrência e rodadas da Saga.
--
-- version: trava otimista. Duas decisões simultâneas na mesma OS (ex.: cliente
-- aprova e equipe recusa) não podem gravar as duas; a segunda falha (409).
--
-- saga_id: identifica a rodada da Saga. Um novo diagnóstico depois de uma falha
-- abre uma rodada nova, e o orquestrador descarta respostas da rodada anterior
-- (o SQS pode reentregá-las). As linhas existentes ficam com o id da OS, que era
-- o saga_id enviado até aqui.
-- =====================================================================

ALTER TABLE saga_state ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE saga_state ADD COLUMN saga_id UUID;
UPDATE saga_state SET saga_id = service_order_id;
ALTER TABLE saga_state ALTER COLUMN saga_id SET NOT NULL;
