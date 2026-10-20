# workbench-os-service — OS Service (Tech Challenge Fase 4)

Microsserviço responsável pelo **ciclo de vida da Ordem de Serviço (OS)** e pela
**orquestração da Saga** da plataforma Workbench. É o **orquestrador central** do
fluxo transacional distribuído (abertura → diagnóstico/reserva → orçamento →
aprovação → execução → pagamento → entrega).

Extraído do monólito `workbench-monolith` (módulos `serviceorder` e `service`) na
refatoração da Fase 4. Mantém **Arquitetura Hexagonal** (domain / application +
ports / adapter in-out / infrastructure).

## Responsabilidades

- Abertura de OS (cliente por CPF/CNPJ, veículo por placa, tipos de serviço)
- **Diagnóstico**: peças a reservar + mão de obra por serviço; inicia a Saga
- Máquina de estados da OS, validada no domínio
- **Orquestração da Saga** (ver seção abaixo) com estado persistido e compensações
- Decisão do orçamento (aprovar/recusar) pelo cliente
- Catálogo de tipos de serviço com custo de mão de obra (editável via API)
- Consulta de status, listagem priorizada e histórico por documento

```
RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → APROVADO → EM_EXECUCAO
  → FINALIZADA → PAGAMENTO_PENDENTE → PAGO → ENTREGUE
                        └→ (recusa) RECUSADA
```

## Estratégia da Saga — ORQUESTRADA (decisão do projeto)

> O PDF pede documentar e justificar a escolha entre orquestração e coreografia.

Escolhemos **Saga orquestrada**, com este serviço (OS Service) como **orquestrador
central**. O estado da Saga é **persistido** (`saga_state`) para sobreviver a
reinícios, com o passo atual, os passos concluídos e o comando pendente.

**Por que orquestrada e não coreografada:**
- O fluxo da OS tem **pausas humanas** (aprovação do orçamento, webhook de
  pagamento) e ramificações (recusa, falha) — mais simples de coordenar e auditar
  num ponto central do que por eventos espalhados entre serviços.
- **Compensação explícita**: o orquestrador sabe exatamente o que desfazer e quando.
- **Rastreabilidade**: um único lugar guarda "onde a Saga está", essencial para
  o monitoramento e o vídeo de demonstração.

**Fluxo e participantes** (comandos via SNS→SQS; respostas via eventos):
1. Diagnóstico → `ComandoReservarEstoque` → Execution responde `ReservaConfirmada`
   (com peças precificadas) ou `ReservaFalhou`.
2. `ComandoGerarOrcamento` (peças + mão de obra) → Billing responde `OrcamentoGerado`
   ou `OrcamentoFalhou`.
3. Aprovação do cliente → `ComandoConfirmarReserva` (baixa de estoque) → `ReservaBaixada`.
4. Fim do serviço → `PAGAMENTO_PENDENTE` → pagamento (Billing/Mercado Pago) →
   `PagamentoConfirmado` → OS `PAGO` → `ENTREGUE`.

**Compensação / rollback** (ver ADR-0005 — "nem toda falha compensa"):
- **Recusa ou falha do orçamento** (reserva ainda `ACTIVE`): compensa liberando a
  reserva (`ComandoLiberarReserva`). Falha de orçamento faz **retry** antes de compensar.
- **Falha na reserva**: aborta, nada a desfazer (tratamento de erro).
- **Falha no pagamento** (serviço já executado, peças no veículo): **não há estorno
  de estoque** — a OS fica **retida** em `PAGAMENTO_PENDENTE`. É tratamento de erro,
  não compensação (reverter estoque criaria inconsistência física).
- **Robustez**: o `saga_state` guarda comando pendente/tentativas/instante; um job
  `@Scheduled` + **ShedLock** reenvia comandos vencidos (timeout) e, esgotadas as
  tentativas, compensa quando a etapa é reversível.

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 + Spring Boot 4.0.5 | Framework web e DI |
| PostgreSQL 16 + Flyway | Banco próprio (SQL) + migrations |
| SNS/SQS (AWS SDK) | Mensageria da Saga (comandos/eventos) |
| ShedLock (JDBC) | Lock distribuído dos jobs agendados (HPA) |
| Spring Security + JWT (HS256) | Validação de token (assinatura compartilhada) |
| Micrometer + Datadog | Métricas de negócio e observabilidade |
| SpringDoc OpenAPI | Swagger UI |
| JaCoCo (gate 80%) + SonarCloud | Qualidade e cobertura |
| Testcontainers | Integração com PostgreSQL real |

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/order` | Criar OS (valida cliente/veículo no Customer Service) |
| GET | `/order` | Listar OS ativas (priorizadas) |
| GET | `/order/id/{id}` | Buscar OS por ID |
| GET | `/order/document/{cpfCnpj}` | Histórico de OS por documento |
| PATCH | `/order/{id}` | Atualizar status da OS |
| POST | `/order/{id}/diagnosis` | Registrar diagnóstico (peças + MO) e iniciar a Saga |
| POST | `/order/{id}/decision` | Aprovar/recusar o orçamento |
| GET/PUT | `/service-types` | Catálogo de tipos de serviço e custo de MO |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Rodar localmente

```bash
# PostgreSQL local (ver application.yml); Flyway cria o schema na subida.
./mvnw spring-boot:run
```

Config relevante (env):
- `MESSAGING_ENABLED` (default `false`): liga SNS/SQS; `false` usa adapters no-op.
- `AWS_ENDPOINT`: vazio = AWS real; `http://localhost:4566` = LocalStack.
- `CUSTOMER_VALIDATION_ENABLED` / `CUSTOMER_SERVICE_URL`: valida cliente/veículo
  por REST no Customer Service (default desligado para dev/testes).

## Testes

```bash
./mvnw verify
```

Unitários + integração (Testcontainers PostgreSQL). Gate de cobertura JaCoCo 80%
no `verify`. Cobertura atual: ~88%.

## CI/CD

Pipeline independente (`.github/workflows/pipeline.yml`):
`unit-test → code-analysis (JaCoCo gate 80% + Sonar) → build → docker-build-push →
deploy-aws (EKS, cluster `workbench-platform-<env>`, quando `USE_AWS=true`)`.

Branch `main` protegida com PR obrigatório e checagens automáticas.

## Relação com os demais serviços

- **Customer Service** (REST): valida cliente/veículo na criação da OS.
- **Execution Service** (mensageria): reserva/baixa/liberação de estoque.
- **Billing Service** (mensageria): orçamento e pagamento (Mercado Pago).
- **workbench-messaging**: tópicos/filas SNS/SQS (Terraform, LocalStack/AWS).

Decisões de arquitetura: `workbench-monolith/docs/adr` (ADR-0003 microsserviços +
Saga; ADR-0005 fluxo da OS/estoque/pagamento/compensação).
