# workbench-os-service — OS Service

Microsserviço de **Ordem de Serviço (OS)** e **orquestrador da Saga** da plataforma
Workbench (Tech Challenge — FIAP Pós Tech, Fase 4).

> **Contexto (Fase 4):** na Fase 3 a oficina era um **monólito** (`workbench-monolith`,
> mantido como base histórica). Na Fase 4 a aplicação foi refatorada em
> **microsserviços independentes**, cada um com seu repositório, infraestrutura e
> banco próprios. Este repositório é o **OS Service** — o coração do fluxo e o
> **orquestrador central** da Saga que coordena os demais serviços. A divisão dos
> serviços e a estratégia da Saga estão documentadas nos ADRs
> (`workbench-monolith/docs/adr` — ADR-0003 e ADR-0005).

---

## Sumário

- [Responsabilidades](#responsabilidades)
- [Requisitos obrigatórios do PDF cobertos aqui](#requisitos-obrigatórios-do-pdf-cobertos-aqui)
- [Estratégia da Saga (orquestrada)](#estratégia-da-saga-orquestrada)
- [Arquitetura da aplicação (hexagonal)](#arquitetura-da-aplicação-hexagonal)
- [Arquitetura de infraestrutura](#arquitetura-de-infraestrutura)
- [Fluxo de Deploy (CI/CD)](#fluxo-de-deploy-cicd)
- [Tecnologias](#tecnologias)
- [Como executar localmente](#como-executar-localmente)
- [Deploy em Kubernetes / AWS](#deploy-em-kubernetes--aws)
- [Referência de endpoints](#referência-de-endpoints)
- [Testes e cobertura](#testes-e-cobertura)
- [Documentação da API (Swagger)](#documentação-da-api-swagger)

---

## Responsabilidades

- Abertura da OS (cliente por CPF/CNPJ, veículo por placa, tipos de serviço)
- **Diagnóstico**: registro das peças a reservar e da mão de obra por serviço; inicia a Saga
- Máquina de estados da OS, validada no domínio
- **Orquestração da Saga**: coordena Execution e Billing, com estado persistido e compensações
- Decisão do orçamento pelo cliente (aprovar/recusar), com **validação de ownership**
  (um cliente só decide a OS do próprio CPF; staff decide qualquer uma)
- Catálogo de tipos de serviço com custo de mão de obra (editável via API)
- Consulta de status, listagem priorizada e **histórico** por documento
- **Notificação de status ao cliente** por e-mail (paridade com o monólito): a cada
  mudança de status, publica uma mensagem formatada num tópico SNS dedicado; o e-mail
  do cliente é resolvido no Customer Service por REST
- **Monitoring**: relatório de tempo médio de execução dos serviços

Fluxo (máquina de estados — ADR-0005):

```
RECEBIDA → EM_DIAGNOSTICO → AGUARDANDO_APROVACAO → APROVADO → EM_EXECUCAO
  → FINALIZADA → PAGAMENTO_PENDENTE → PAGO → ENTREGUE
                        └→ (recusa) RECUSADA
```

---

## Requisitos obrigatórios do PDF cobertos aqui

| Requisito (PDF Fase 4) | Como este serviço atende |
|---|---|
| ≥3 microsserviços independentes | É um dos 4 serviços (OS, Billing, Execution, Customer), com repo/infra/banco próprios |
| Banco próprio | **PostgreSQL** exclusivo; nenhum outro serviço o acessa |
| SQL **e** NoSQL (no conjunto) | Contribui com o **SQL** (PostgreSQL); o NoSQL fica no Execution (MongoDB) |
| Nenhum acesso ao banco de outro serviço | Valida cliente/veículo no Customer via **REST**; fala com Execution/Billing via **mensageria** |
| REST + mensageria assíncrona | API REST (ciclo da OS) + **SNS/SQS** (comandos/eventos da Saga) |
| **Saga Pattern + rollback/compensação** | **Orquestrador central** (ver seção da Saga); compensação na recusa/falha de orçamento |
| Testes + 80% cobertura + Sonar | JaCoCo com gate de 80% no `verify` + SonarCloud no CI (cobertura atual ~88%) |
| CI/CD + deploy K8s + branch protegida | Pipeline próprio (build→testes→qualidade→imagem→deploy EKS); `main` protegida |

---

## Estratégia da Saga (orquestrada)

> O PDF exige **documentar e justificar** a escolha entre orquestração e coreografia.

Escolhemos **Saga orquestrada**, com o **OS Service como orquestrador central**. O
estado da Saga é **persistido** (tabela `saga_state`: passo atual, passos concluídos,
comando pendente, tentativas) para sobreviver a reinícios.

**Por que orquestrada e não coreografada:**
- O fluxo tem **pausas humanas** (aprovação do orçamento, webhook de pagamento) e
  ramificações (recusa, falha) — mais simples de coordenar e auditar em um ponto central.
- **Compensação explícita**: o orquestrador sabe exatamente o que desfazer e quando.
- **Rastreabilidade**: um único lugar guarda "onde a Saga está" — essencial para o
  monitoramento e a demonstração.

**Fluxo (comandos via SNS→SQS; respostas via eventos):**

```
Diagnóstico ──ComandoReservarEstoque──▶ Execution ──ReservaConfirmada/ReservaFalhou──▶
  ──ComandoGerarOrcamento──▶ Billing ──OrcamentoGerado/OrcamentoFalhou──▶
  (aprovação do cliente) ──ComandoConfirmarReserva──▶ Execution ──ReservaBaixada──▶
  ... execução ... ──ComandoCobrarPagamento──▶ Billing ──PagamentoConfirmado──▶ PAGO → ENTREGUE
```

**Compensação / rollback** (princípio do ADR-0005 — *nem toda falha compensa; só se
reverte o que foi efetivado e precisa ser desfeito para manter consistência*):

| Falha | Já efetivado | Ação |
|---|---|---|
| Reserva falha | nada | aborta (tratamento de erro) |
| Orçamento falha (pós-reserva) | reserva `ACTIVE` | **retry**; esgotado → **compensa** liberando a reserva |
| Recusa do orçamento | reserva `ACTIVE` | **compensa** liberando a reserva |
| Pagamento falha (pós-execução) | estoque baixado + peça instalada | **retém** em `PAGAMENTO_PENDENTE` (não reverte estoque) |

**Robustez:** o `saga_state` guarda o comando pendente e o instante do envio; um job
`@Scheduled` + **ShedLock** reenvia comandos vencidos (timeout) e, esgotadas as
tentativas, compensa quando a etapa é reversível.

---

## Arquitetura da aplicação (hexagonal)

Arquitetura **Hexagonal (Ports & Adapters)**: o domínio não depende de frameworks.

```
src/main/java/com/workbench/workshop/
├── adapter/
│   ├── in/web/            # Controllers REST (serviceorder, servicetype)
│   └── out/persistence/   # Adapters JPA + adapter de Saga
├── application/           # Use cases + ports
│   ├── saga/              # SagaOrchestrator + ports (SagaCommandPort, SagaStateRepository)
│   ├── serviceorder/      # Create/Update/Diagnose/Decide/Find + ports
│   └── service/           # Tipos de serviço e serviços da OS
├── domain/                # Domínio puro
│   ├── saga/              # SagaState, SagaStep
│   ├── serviceorder/      # ServiceOrder (rich domain), máquina de estados, Decision
│   └── service/           # WorkshopService, ServiceType
└── infrastructure/        # config, persistence (JPA), messaging (SNS/SQS), scheduling (ShedLock), security
```

---

## Arquitetura de infraestrutura

Cada microsserviço tem **banco próprio** e roda em um **namespace** dedicado dentro
de um **cluster EKS compartilhado** pela plataforma (`workbench-platform-<env>`,
provisionado pelo `workbench-k8s-terraform`). Não há acesso cruzado a bancos.

```
┌───────────────────────── EKS: workbench-platform-<env> ─────────────────────────┐
│  ns: os-service        ns: billing-service   ns: execution-service  ns: customer │
│  ┌──────────────┐      ┌──────────────┐      ┌──────────────┐    ┌────────────┐  │
│  │ os-service   │      │ billing      │      │ execution    │    │ customer   │  │
│  │ + PostgreSQL │      │ + PostgreSQL │      │ + MongoDB    │    │ +PostgreSQL│  │
│  └──────┬───────┘      └──────────────┘      └──────────────┘    └────────────┘  │
│         │ orquestra via SNS/SQS (workbench-messaging)                            │
└─────────┼────────────────────────────────────────────────────────────────────── ┘
          └─▶ SNS tópicos / SQS filas + DLQ (repo workbench-messaging)
```

- **Banco**: PostgreSQL próprio, schema versionado por **Flyway**.
- **Mensageria**: tópicos/filas provisionados no repo `workbench-messaging` (SNS/SQS,
  LocalStack em dev / AWS em produção).

---

## Fluxo de Deploy (CI/CD)

Pipeline independente (`.github/workflows/pipeline.yml`):

```
push (main/develop) ou PR
   │
   ▼
unit-test ──▶ code-analysis ──▶ build ──▶ docker-build-push ──▶ deploy-aws
(mvn verify   (JaCoCo gate 80%  (mvn      (imagem p/ Docker Hub  (EKS, só se
 + JaCoCo      + Sonar)          package)  usuario/os-service)    USE_AWS=true)
 badge)
```

- Gate de cobertura **80%** (JaCoCo `check`) na fase `verify`.
- `deploy-aws` anexa ao cluster `workbench-platform-<branch>` e aplica os manifests `k8s/`.

### Secrets/variáveis (GitHub Actions)

| Nome | Escopo | Uso |
|---|---|---|
| `DOCKER_USERNAME`, `DOCKER_HUB_TOKEN` | org | push da imagem |
| `SONAR_TOKEN` | org/repo | análise SonarCloud (sem ele, roda só o gate local) |
| `AWS_ACCESS_KEY_ID_<ENV>`, `AWS_SECRET_ACCESS_KEY_<ENV>`, `AWS_REGION` | org | deploy EKS |
| `USE_AWS` (variable) | repo | `true` habilita o deploy no EKS |

---

## Tecnologias

| Tecnologia | Finalidade |
|---|---|
| Java 21 + Spring Boot 4.0.5 | Framework web e DI |
| PostgreSQL 16 + Flyway | Banco próprio (SQL) + migrations |
| AWS SNS/SQS (SDK v2) | Mensageria da Saga (comandos/eventos) + DLQ |
| ShedLock (JDBC) | Lock distribuído dos jobs agendados (HPA) |
| Spring Security + JWT (HS256) | Validação de token (assinatura compartilhada) |
| Micrometer + Datadog | Métricas de negócio e observabilidade |
| SpringDoc OpenAPI | Swagger UI |
| JaCoCo (gate 80%) + SonarCloud | Qualidade e cobertura |
| Testcontainers | Testes de integração com PostgreSQL real |

---

## Como executar localmente

Pré-requisitos: Java 21, Docker (para Testcontainers), um PostgreSQL acessível.

```bash
./mvnw spring-boot:run
```

O Flyway cria o schema na subida. Configuração relevante (env):

| Variável | Default | Descrição |
|---|---|---|
| `MESSAGING_ENABLED` | `false` | Liga SNS/SQS; `false` usa adapters no-op (sem broker) |
| `AWS_ENDPOINT` | — | vazio = AWS real; `http://localhost:4566` = LocalStack |
| `CUSTOMER_VALIDATION_ENABLED` | `false` | Valida cliente/veículo no Customer via REST; também habilita a busca do e-mail do cliente para a notificação |
| `CUSTOMER_SERVICE_URL` | — | URL base do Customer Service |
| `NOTIFICATIONS_TOPIC_ARN` | — | Tópico SNS de notificação de status ao cliente (e-mail). Sem ele, a notificação é ignorada |

---

## Deploy em Kubernetes / AWS

Manifests em `k8s/` (`namespace`, `configmap`, `secret`, `app`, `hpa`). O deploy é
automatizado pelo pipeline (job `deploy-aws`, quando `USE_AWS=true`), que roda
`aws eks update-kubeconfig --name workbench-platform-<branch>` e aplica os manifests
no namespace `os-service`. Provisionar o cluster/credenciais AWS é pré-requisito
(ver `workbench-k8s-terraform` e `PENDENCIAS.md`).

---

## Referência de endpoints

| Método | Rota | Descrição |
|---|---|---|
| POST | `/order` | Criar OS (valida cliente/veículo no Customer Service) |
| GET | `/order` | Listar OS ativas (priorizadas) |
| GET | `/order/id/{id}` | Buscar OS por ID |
| GET | `/order/document/{cpfCnpj}` | Histórico de OS por documento |
| PATCH | `/order/{id}` | Atualizar status da OS |
| POST | `/order/{id}/diagnosis` | Registrar diagnóstico (peças + MO) e iniciar a Saga |
| POST | `/order/{id}/decision` | Aprovar/recusar o orçamento (cliente só decide a própria OS; staff decide qualquer uma) |
| GET | `/service-types` | Listar tipos de serviço (com custo de MO) |
| PUT | `/service-types/{id}/labor-cost` | Atualizar custo/horas de MO de um tipo |
| POST | `/monitoring/all` | Tempo médio de execução por tipo de serviço (SECONDS/MINUTES/HOURS) |
| POST | `/monitoring/by-id` | Tempo de execução de um serviço específico |

> **Autorização:** rotas `/order/document/**` aceitam CLIENT/USER/ADMIN/TECHNICIAN;
> as demais exigem ADMIN/TECHNICIAN. Na decisão do orçamento, um token de **cliente**
> só pode decidir a OS do próprio CPF (ownership); staff decide qualquer OS.

> **Chamadas internas (token de serviço):** ao validar cliente/veículo e resolver o
> e-mail no Customer Service, o OS Service usa um **token de serviço** (staff, curta
> duração) emitido pelo `ServiceTokenProvider` com o `JWT_SECRET` compartilhado — porque
> todos os serviços agora exigem token, inclusive em threads assíncronas da Saga.

> **Pagamento automático + link ao cliente:** quando a OS entra em `PAGAMENTO_PENDENTE`,
> o OS Service comanda o Billing a criar a cobrança (Saga); ao receber `CobrancaCriada`,
> envia ao cliente o **link de checkout** por e-mail. O cliente também pode consultar o
> link em `GET {billing}/api/payments/service-order/{osId}` (campo `checkoutUrl`).

> **Monitoring** (paridade com o monólito): relatório gerencial de tempo médio de
> execução, medido entre o primeiro `DOING` e o primeiro `DONE` de cada serviço.

---

## Testes e cobertura

```bash
./mvnw verify          # unit + integração (Testcontainers) + gate de cobertura
```

- **Gate JaCoCo 80%** na fase `verify` (o build falha abaixo disso).
- Integração com **Testcontainers PostgreSQL** (sobe um Postgres real em container).
- Cobertura atual: **~88%**. O relatório fica em `target/site/jacoco/index.html`.

---

## Documentação da API (Swagger)

| Recurso | URL (local) |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:8080/v3/api-docs |

> **Collection Postman** deste serviço: `postman/os-service.postman_collection.json`.
> Collection unificada da plataforma (+ fluxo Saga E2E): `workbench-technical-documentation/postman`.
