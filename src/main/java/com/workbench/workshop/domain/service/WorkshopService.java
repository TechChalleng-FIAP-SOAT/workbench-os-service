package com.workbench.workshop.domain.service;

import com.workbench.workshop.domain.serviceorder.InvalidStatusTransitionException;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

    /** Novo serviço da OS: começa em {@code TO_DO}, com a mão de obra já resolvida. */
    public static WorkshopService create(String serviceTypeName, UUID idOS, BigDecimal laborCost,
                                         BigDecimal laborHours, LocalDateTime at) {
        WorkshopService service = new WorkshopService(null, serviceTypeName, idOS,
                List.of(new Status(ServiceStatusEnum.TO_DO, at)));
        service.defineLabor(laborCost, laborHours);
        return service;
    }

    /** Recria o serviço a partir do que está persistido. */
    public static WorkshopService reconstitute(UUID id, String serviceTypeName, UUID idOS, List<Status> serviceStatus,
                                               BigDecimal laborCost, BigDecimal laborHours) {
        WorkshopService service = new WorkshopService(id, serviceTypeName, idOS, serviceStatus);
        service.defineLabor(laborCost, laborHours);
        return service;
    }

    /** Mão de obra deste serviço (definida no diagnóstico); custo nulo vira zero. */
    public void defineLabor(BigDecimal newLaborCost, BigDecimal newLaborHours) {
        this.laborCost = newLaborCost == null ? BigDecimal.ZERO : newLaborCost;
        this.laborHours = newLaborHours;
    }

    /** Status atual: o último registrado (TO_DO quando ainda não há histórico). */
    public ServiceStatusEnum currentStatus() {
        return serviceStatus == null || serviceStatus.isEmpty()
                ? ServiceStatusEnum.TO_DO
                : serviceStatus.get(serviceStatus.size() - 1).getStatus();
    }

    /**
     * Avança o serviço na execução: {@code TO_DO → DOING → DONE}, registrando o instante
     * (o relatório de tempo médio mede do primeiro DOING ao primeiro DONE).
     */
    public void moveTo(ServiceStatusEnum target, LocalDateTime at) {
        ServiceStatusEnum next = nextStatus();
        if (target != next) {
            throw new InvalidStatusTransitionException("Serviço " + serviceTypeName + " está " + currentStatus()
                    + (next == null ? " e já foi concluído." : "; a próxima etapa permitida é " + next + "."));
        }
        List<Status> history = new ArrayList<>(serviceStatus == null ? List.of() : serviceStatus);
        history.add(new Status(target, at));
        this.serviceStatus = history;
    }

    /** Próxima etapa da execução, ou {@code null} quando já está DONE. */
    private ServiceStatusEnum nextStatus() {
        return switch (currentStatus()) {
            case TO_DO -> ServiceStatusEnum.DOING;
            case DOING -> ServiceStatusEnum.DONE;
            case DONE -> null;
        };
    }

    public boolean isDone() {
        return currentStatus() == ServiceStatusEnum.DONE;
    }

    /**
     * Regra da mão de obra (ADR-0005): o valor informado vence; sem valor,
     * {@code useDefault} puxa o default do catálogo, senão usa o {@code fallback}.
     */
    public static BigDecimal resolveLabor(BigDecimal informed, BigDecimal catalogDefault,
                                          boolean useDefault, BigDecimal fallback) {
        if (informed != null) {
            return informed;
        }
        return useDefault ? catalogDefault : fallback;
    }
}
