package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.application.saga.SagaOrchestrator.ReserveItem;
import com.workbench.workshop.application.serviceorder.DiagnoseOrderUseCase.ServiceLabor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Payload do diagnóstico da OS: peças a reservar e mão de obra por serviço.
 *
 * @param parts    peças (productId + quantidade) a reservar no estoque
 * @param services mão de obra por serviço da OS (custo/horas + flag useDefault)
 */
public record DiagnoseOrderRequest(
        List<PartItem> parts,
        List<ServiceLaborItem> services
) {
    public record PartItem(String productId, BigDecimal quantity) {}

    public record ServiceLaborItem(String serviceType, BigDecimal laborCost, BigDecimal laborHours, Boolean useDefault) {}

    /** Peças no formato do caso de uso (lista vazia quando ausente). */
    public List<ReserveItem> reserveItems() {
        return parts == null ? List.of()
                : parts.stream().map(p -> new ReserveItem(p.productId(), p.quantity())).toList();
    }

    /** Mão de obra no formato do caso de uso ({@code useDefault} ausente = false). */
    public List<ServiceLabor> labor() {
        return services == null ? List.of()
                : services.stream()
                    .map(s -> new ServiceLabor(s.serviceType(), s.laborCost(), s.laborHours(),
                            Boolean.TRUE.equals(s.useDefault())))
                    .toList();
    }
}
