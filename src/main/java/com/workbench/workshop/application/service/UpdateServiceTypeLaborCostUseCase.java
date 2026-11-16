package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/** Atualiza só o custo (e as horas) de mão de obra de um tipo de serviço. */
@Service
@RequiredArgsConstructor
public class UpdateServiceTypeLaborCostUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public ServiceType execute(UUID id, BigDecimal laborCost, BigDecimal laborHours) {
        ServiceType type = serviceTypeRepository.getById(id);
        type.changeLabor(laborCost, laborHours);
        return serviceTypeRepository.save(type);
    }
}
