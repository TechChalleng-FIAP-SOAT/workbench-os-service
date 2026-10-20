# os-service — OS Service (Tech Challenge Fase 4)

Microsserviço responsável pelo **ciclo de vida da Ordem de Serviço (OS)** e, nas
próximas etapas, pela **orquestração da Saga** da plataforma OS Management.

Extraído do monólito `workbench-monolith` (módulos `serviceorder` e `service`) na
refatoração para microsserviços da Fase 4. Mantém a **Arquitetura Hexagonal**
(domain / application + ports / adapter in-out / infrastructure).

## Responsabilidades

- Abertura de OS (cliente por CPF/CNPJ, veículo por placa, tipos de serviço)
- Máquina de estados da OS, validada no domínio
- Consulta de status, listagem priorizada e histórico por documento
- Atualização de status com transições validadas

```
RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → APROVADO → EM_EXECUCAO → FINALIZADA → ENTREGUE
                                   └→ RECUSADA → (reabertura) → EM_DIAGNOSTICO
```

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 + Spring Boot 4.0.5 | Framework web e DI |
| PostgreSQL 16 | Banco próprio do serviço (SQL) |
| Flyway | Migrations versionadas do schema |
| Spring Security + JWT (HS256) | Validação de token (assinatura compartilhada) |
| Micrometer + Datadog | Métricas de negócio e observabilidade |
| SpringDoc OpenAPI | Swagger UI |
| JaCoCo (gate 80%) + SonarCloud | Qualidade e cobertura |
| Testcontainers | Testes de integração com PostgreSQL real |

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/order` | Criar OS |
| GET | `/order` | Listar OS ativas (priorizadas) |
| GET | `/order/id/{id}` | Buscar OS por ID |
| GET | `/order/document/{cpfCnpj}` | Histórico de OS por documento |
| PATCH | `/order/{id}` | Atualizar status da OS |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Rodar localmente

```bash
# Sobe um PostgreSQL local na porta 15432 (ajuste conforme seu ambiente)
mvn spring-boot:run
```

O Flyway cria o schema (`V1__init_os_service_schema.sql`) e popula tipos de
serviço de exemplo (`V2__seed_service_types.sql`) na subida.

## Diferenças em relação ao monólito (Fase 4)

- **Banco próprio** (`os_service`), isolado dos demais serviços.
- **MetricsPort / NotificationPort**: os use cases agora dependem de portas, não
  de classes de infraestrutura — remove o vazamento `application → infrastructure`
  que existia no monólito.
- **Validação de cliente/veículo**: migrou para o Customer Service. A criação de
  OS marca um `TODO` para a chamada REST ao Customer (Etapa 6).
- **Notificação / mensageria** (Etapa 2): a `OrderNotificationPort` tem dois
  adapters, alternados por `messaging.enabled`:
  - `false` (default): `LoggingOrderNotificationAdapter` — roda sem broker (dev/testes).
  - `true`: `SnsOrderEventPublisher` — publica o evento `StatusAlterado` no tópico
    SNS `workbench-order-events`. O `SagaReplyConsumer` faz long-polling da fila
    `workbench-os-saga-replies-queue` com dedupe por `messageId` e redrive p/ DLQ.
  - `AWS_ENDPOINT` vazio = AWS real; `http://localhost:4566` = LocalStack
    (ver repo `workbench-messaging`).

## CI/CD

Pipeline independente (`.github/workflows/pipeline.yml`):
`unit-test → code-analysis (JaCoCo gate 80% + Sonar) → build → docker-build-push → deploy-aws (EKS)`.

Branch `main` deve ser protegida com PR obrigatório e checagens automáticas.

## Status na Fase 4

- [x] Extração hexagonal de `serviceorder` + `service`
- [x] Banco próprio + Flyway
- [x] Quick wins de clean code (MetricsPort, FKs/índices, CHECK de status)
- [ ] Publisher/consumer SNS/SQS (Etapa 2)
- [ ] Orquestração da Saga (Etapa 5)
- [ ] Validação REST ao Customer Service (Etapa 6)
