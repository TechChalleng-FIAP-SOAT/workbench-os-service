package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FindOrderByIdUseCase {

    private final ServiceOrderRepository serviceOrderRepository;

    @Transactional(readOnly = true)
    public ServiceOrder execute(UUID id) {
        return serviceOrderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Ordem de servico nao encontrada. ID: " + id));
    }
}
