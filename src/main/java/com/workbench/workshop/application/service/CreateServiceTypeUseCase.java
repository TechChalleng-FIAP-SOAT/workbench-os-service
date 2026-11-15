package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** Adiciona um tipo de serviço ao catálogo; o nome (normalizado) é único. */
@Service
@RequiredArgsConstructor
public class CreateServiceTypeUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public ServiceType execute(String name, String description, BigDecimal laborCost, BigDecimal laborHours) {
        ServiceType type = ServiceType.create(name, description, laborCost, laborHours);
        if (serviceTypeRepository.existsByName(type.getName())) {
            throw new IllegalStateException("Já existe tipo de serviço com o nome " + type.getName() + ".");
        }
        return serviceTypeRepository.save(type);
    }
}
