-- =====================================================================
-- OS Service — schema inicial (Fase 4, Etapa 1)
-- Extraído do schema do monólito (service_order, service, service_type),
-- agora como banco próprio do serviço. Inclui os quick wins de banco:
-- índices nas colunas de busca e CHECK constraints de status.
-- =====================================================================

-- Tipos de serviço do catálogo (ex.: TROCA_OLEO, ALINHAMENTO)
CREATE TABLE service_type (
    id          UUID PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE,
    description VARCHAR(1000)
);

-- Ordem de serviço (agregado central)
CREATE TABLE service_order (
    id                UUID PRIMARY KEY,
    service_type_name VARCHAR(1000) NOT NULL,
    service_status    VARCHAR(50)   NOT NULL,
    list_service      TEXT          NOT NULL DEFAULT '[]',
    cpf_cnpj          VARCHAR(20)   NOT NULL,
    placa             VARCHAR(10)   NOT NULL,
    rejection_reason  VARCHAR(1000),
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL,
    CONSTRAINT chk_service_order_status CHECK (service_status IN (
        'RECEBIDA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'APROVADO',
        'EM_EXECUCAO', 'FINALIZADA', 'ENTREGUE', 'RECUSADA'
    ))
);

-- Índices nas colunas usadas em WHERE (quick win: no monólito não existiam,
-- causando sequential scan em GET /order/document/{cpfCnpj}).
CREATE INDEX idx_service_order_cpf_cnpj ON service_order (cpf_cnpj);
CREATE INDEX idx_service_order_placa    ON service_order (placa);
CREATE INDEX idx_service_order_status   ON service_order (service_status);

-- Serviços individuais dentro de uma OS
CREATE TABLE service (
    id                UUID PRIMARY KEY,
    service_type_name VARCHAR(255) NOT NULL,
    id_os             UUID         NOT NULL,
    service_status    TEXT         NOT NULL DEFAULT '[]',
    CONSTRAINT fk_service_service_order FOREIGN KEY (id_os)
        REFERENCES service_order (id) ON DELETE CASCADE
);

CREATE INDEX idx_service_id_os ON service (id_os);
