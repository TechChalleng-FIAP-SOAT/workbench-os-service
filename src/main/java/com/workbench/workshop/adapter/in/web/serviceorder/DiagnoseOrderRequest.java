package com.workbench.workshop.adapter.in.web.serviceorder;

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
        List<ServiceLabor> services
) {
    public record PartItem(String productId, BigDecimal quantity) {}

    public record ServiceLabor(String serviceType, BigDecimal laborCost, BigDecimal laborHours, Boolean useDefault) {}
}
