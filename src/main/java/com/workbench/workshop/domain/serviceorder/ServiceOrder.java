package com.workbench.workshop.domain.serviceorder;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
public class ServiceOrder {

    private UUID id;
    private String serviceTypeName;
    private OrderServiceStatusEnum status;
    private List<String> listService;
    private String cpfCnpj;
    private String licensePlate;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    protected ServiceOrder() {}

    public static ServiceOrder create(String cpfCnpj, String licensePlate, List<String> serviceTypes) {
        Objects.requireNonNull(cpfCnpj, "CPF/CNPJ é obrigatório");
        Objects.requireNonNull(licensePlate, "Placa do veículo é obrigatória");
        if (serviceTypes == null || serviceTypes.isEmpty()) {
            throw new IllegalArgumentException("Informe ao menos um tipo de serviço");
        }

        var order = new ServiceOrder();
        order.id = UUID.randomUUID();
        order.cpfCnpj = normalizeDocument(cpfCnpj);
        order.licensePlate = licensePlate.strip().toUpperCase();
        order.listService = List.copyOf(serviceTypes);
        order.serviceTypeName = serviceTypes.toString();
        order.status = OrderServiceStatusEnum.RECEBIDA;
        return order;
    }

    public static ServiceOrder reconstitute(UUID id, String cpfCnpj, String licensePlate,
                                             List<String> serviceTypes, String serviceTypeName,
                                             OrderServiceStatusEnum status, String rejectionReason,
                                             LocalDateTime createdAt, LocalDateTime updatedAt) {
        var order = new ServiceOrder();
        order.id = id;
        order.cpfCnpj = cpfCnpj;
        order.licensePlate = licensePlate;
        order.listService = serviceTypes;
        order.serviceTypeName = serviceTypeName;
        order.status = status;
        order.rejectionReason = rejectionReason;
        order.createdAt = createdAt;
        order.updatedAt = updatedAt;
        return order;
    }

    public void advanceTo(OrderServiceStatusEnum newStatus) {
        Objects.requireNonNull(newStatus, "Status de destino é obrigatório");
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(
                    "Não é possível mudar de " + this.status.getStatus()
                            + " para " + newStatus.getStatus()
                            + ". Transições permitidas: " + this.status.getAllowedTransitions());
        }
        this.status = newStatus;
    }

    public void reject(String reason) {
        advanceTo(OrderServiceStatusEnum.RECUSADA);
        this.rejectionReason = reason;
    }

    public void reopen() {
        advanceTo(OrderServiceStatusEnum.EM_DIAGNOSTICO);
    }

    public String getServiceStatus() {
        return status.getStatus();
    }

    /** CPF/CNPJ só com dígitos — formato único para gravar, buscar e comparar com o token. */
    public static String normalizeDocument(String cpfCnpj) {
        return cpfCnpj == null ? "" : cpfCnpj.replaceAll("[^0-9]", "");
    }
}
