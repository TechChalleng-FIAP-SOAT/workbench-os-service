-- Catálogo mínimo de tipos de serviço para desenvolvimento/testes locais.
-- Em produção, o catálogo é gerenciado pela própria API.
INSERT INTO service_type (id, name, description) VALUES
    (gen_random_uuid(), 'TROCA_OLEO',  'Troca de óleo e filtro'),
    (gen_random_uuid(), 'ALINHAMENTO', 'Alinhamento e balanceamento'),
    (gen_random_uuid(), 'REVISAO',     'Revisão geral preventiva')
ON CONFLICT (name) DO NOTHING;
