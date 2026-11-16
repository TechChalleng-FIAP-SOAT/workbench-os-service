package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.domain.service.ServiceStatusEnum;
import jakarta.validation.constraints.NotNull;

/** Próxima etapa de execução de um serviço da OS: {@code DOING} (iniciar) ou {@code DONE} (concluir). */
public record UpdateServiceStatusRequest(@NotNull ServiceStatusEnum status) {}
