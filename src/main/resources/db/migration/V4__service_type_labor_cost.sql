-- =====================================================================
-- V4 — Mão de obra no tipo de serviço (ADR-0005 / PDF real de referência).
-- Preço fixo de mão de obra por tipo de serviço, editável via API.
-- labor_time é opcional (coluna "Tempo MO" do orçamento real).
-- =====================================================================

ALTER TABLE service_type ADD COLUMN labor_cost NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE service_type ADD COLUMN labor_time VARCHAR(50);
