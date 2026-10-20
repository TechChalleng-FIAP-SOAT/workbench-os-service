package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Casos de uso do catálogo de tipos de serviço, incluindo o preço de mão de obra. */
@Service
@RequiredArgsConstructor
public class ServiceTypeUseCases {

    private final ServiceTypeRepository serviceTypeRepository;

    public List<ServiceType> findAll() {
        return serviceTypeRepository.findAll();
    }

    public ServiceType findByName(String name) {
        return serviceTypeRepository.findByName(name)
                .orElseThrow(() -> new NoSuchElementException("Tipo de serviço não encontrado: " + name));
    }

    /** Atualiza o custo (e horas) de mão de obra de um tipo de serviço. */
    public ServiceType updateLaborCost(UUID id, BigDecimal laborCost, BigDecimal laborHours) {
        if (laborCost == null || laborCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("laborCost deve ser >= 0");
        }
        if (laborHours != null && laborHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("laborHours deve ser >= 0");
        }
        ServiceType type = serviceTypeRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Tipo de serviço não encontrado: " + id));
        type.setLaborCost(laborCost);
        type.setLaborHours(laborHours);
        return serviceTypeRepository.save(type);
    }
}
