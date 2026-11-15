package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.domain.service.WorkshopService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Serviço de uma OS: tipo, mão de obra, status atual e histórico de execução. */
public record OrderServiceResponse(
        UUID id,
        String serviceType,
        BigDecimal laborCost,
        BigDecimal laborHours,
        String status,
        List<StatusChange> history
) {
    public record StatusChange(String status, LocalDateTime changedAt) {}

    public static OrderServiceResponse from(WorkshopService s) {
        List<Status> statuses = s.getServiceStatus() == null ? List.of() : s.getServiceStatus();
        return new OrderServiceResponse(s.getId(), s.getServiceTypeName(), s.getLaborCost(), s.getLaborHours(),
                s.currentStatus().getStatus(),
                statuses.stream().map(st -> new StatusChange(st.getStatus().getStatus(), st.getChangedAt())).toList());
    }
}
