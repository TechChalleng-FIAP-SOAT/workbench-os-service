-- =====================================================================
-- V5 — Estado da Saga orquestrada (ADR-0003/0005).
-- Uma linha por OS; guarda o passo atual e os passos concluídos (p/ compensação).
-- =====================================================================

CREATE TABLE saga_state (
    id               UUID PRIMARY KEY,
    service_order_id UUID NOT NULL UNIQUE,
    step             VARCHAR(40) NOT NULL,
    completed_steps  VARCHAR(1000),
    failure_reason   VARCHAR(1000),
    created_at       TIMESTAMP NOT NULL,
    updated_at       TIMESTAMP NOT NULL
);

CREATE INDEX idx_saga_state_service_order ON saga_state (service_order_id);
