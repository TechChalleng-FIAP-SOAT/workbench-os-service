package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Atualiza um tipo de serviço: descrição e mão de obra (custo e horas). O nome não
 * muda — OS e diagnóstico encontram o tipo por ele.
 */
@Service
@RequiredArgsConstructor
public class UpdateServiceTypeUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public ServiceType execute(UUID id, String description, BigDecimal laborCost, BigDecimal laborHours) {
        ServiceType type = serviceTypeRepository.getById(id);
        type.update(description, laborCost, laborHours);
        return serviceTypeRepository.save(type);
    }
}
