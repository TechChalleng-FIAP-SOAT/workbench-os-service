-- =====================================================================
-- V6 — Mão de obra por serviço da OS (diagnóstico) + horas como número.
--
-- Decisões (ADR-0005):
--  - No diagnóstico, o técnico informa, por serviço, o valor da MO (labor_cost)
--    e, opcionalmente, as horas (labor_hours). Esses valores são próprios da OS,
--    tendo o catálogo (service_type) apenas como default (regra useDefault na API).
--  - O "Tempo MO" passa a ser NUMÉRICO (horas), não texto livre: a coluna
--    service_type.labor_time (VARCHAR, criada em V4) é substituída por
--    service_type.labor_hours (NUMERIC).
-- =====================================================================

-- 1) MO por serviço concreto da OS (tabela `service`).
ALTER TABLE service ADD COLUMN labor_cost  NUMERIC(15,2) NOT NULL DEFAULT 0;
ALTER TABLE service ADD COLUMN labor_hours NUMERIC(10,2);

-- 2) Catálogo: trocar labor_time (texto) por labor_hours (número).
ALTER TABLE service_type ADD COLUMN labor_hours NUMERIC(10,2);
ALTER TABLE service_type DROP COLUMN labor_time;
