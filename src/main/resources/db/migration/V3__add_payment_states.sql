-- =====================================================================
-- V3 — Novos estados do fluxo da OS (ADR-0005): PAGAMENTO_PENDENTE e PAGO.
-- Pagamento ocorre no fim do fluxo; ENTREGUE só após PAGO.
-- =====================================================================

ALTER TABLE service_order DROP CONSTRAINT IF EXISTS chk_service_order_status;

ALTER TABLE service_order ADD CONSTRAINT chk_service_order_status CHECK (service_status IN (
    'RECEBIDA', 'EM_DIAGNOSTICO', 'AGUARDANDO_APROVACAO', 'APROVADO',
    'EM_EXECUCAO', 'FINALIZADA', 'PAGAMENTO_PENDENTE', 'PAGO', 'ENTREGUE', 'RECUSADA'
));
