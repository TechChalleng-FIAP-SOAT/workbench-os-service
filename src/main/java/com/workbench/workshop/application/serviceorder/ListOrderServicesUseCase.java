package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Serviços de uma OS, com o status de execução e o histórico de cada um. */
@Service
@RequiredArgsConstructor
public class ListOrderServicesUseCase {

    private final ServiceOrderRepository serviceOrderRepository;
    private final ServiceRepository serviceRepository;

    @Transactional(readOnly = true)
    public List<WorkshopService> execute(UUID orderId) {
        if (serviceOrderRepository.findById(orderId).isEmpty()) {
            throw new NoSuchElementException("Ordem de serviço não encontrada: " + orderId);
        }
        return serviceRepository.findByIdOS(orderId);
    }
}
