package com.workbench.workshop.domain.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class WorkshopService {

    private UUID id;
    private String serviceTypeName;
    private UUID idOS;
    private List<Status> serviceStatus;

    /** Valor de mão de obra deste serviço na OS (definido no diagnóstico). */
    private BigDecimal laborCost = BigDecimal.ZERO;

    /** Horas de MO deste serviço (opcional, numérico). */
    private BigDecimal laborHours;

    public WorkshopService(UUID id, String serviceTypeName, UUID idOS, List<Status> serviceStatus) {
        this.id = id;
        this.serviceTypeName = serviceTypeName;
        this.idOS = idOS;
        this.serviceStatus = serviceStatus;
    }
}
