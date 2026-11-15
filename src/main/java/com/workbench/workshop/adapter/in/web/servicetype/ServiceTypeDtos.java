package com.workbench.workshop.adapter.in.web.servicetype;

import com.workbench.workshop.domain.service.ServiceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/** DTOs de entrada/saída do catálogo de tipos de serviço. */
public final class ServiceTypeDtos {

    private ServiceTypeDtos() {}

    /** Novo tipo; o nome é normalizado (ex.: "troca de óleo" → TROCA_DE_ÓLEO) e não muda depois. */
    public record CreateServiceTypeRequest(
            @NotBlank String name,
            String description,
            @PositiveOrZero BigDecimal laborCost,
            @PositiveOrZero BigDecimal laborHours) {}

    /** Atualização completa (o nome não é alterável). */
    public record UpdateServiceTypeRequest(
            String description,
            @NotNull @PositiveOrZero BigDecimal laborCost,
            @PositiveOrZero BigDecimal laborHours) {}

    public record UpdateLaborCostRequest(
            @NotNull @PositiveOrZero BigDecimal laborCost,
            @PositiveOrZero BigDecimal laborHours) {}

    public record ServiceTypeResponse(UUID id, String name, String description,
                                      BigDecimal laborCost, BigDecimal laborHours) {
        static ServiceTypeResponse from(ServiceType t) {
            return new ServiceTypeResponse(t.getId(), t.getName(), t.getDescription(),
                    t.getLaborCost(), t.getLaborHours());
        }
    }
}
