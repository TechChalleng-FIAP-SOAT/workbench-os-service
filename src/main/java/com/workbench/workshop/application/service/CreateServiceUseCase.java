package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import com.workbench.workshop.domain.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateServiceUseCase {

    private final ServiceRepository serviceRepository;
    private final ServiceTypeRepository serviceTypeRepository;

    /** Cria o serviço sem mão de obra explícita (MO = 0). */
    public WorkshopService execute(String serviceType, UUID idOS) {
        return execute(serviceType, idOS, null, null, false);
    }

    /** Cria o serviço resolvendo a mão de obra pela regra de {@link WorkshopService#resolveLabor}. */
    public WorkshopService execute(String serviceType, UUID idOS,
                                   BigDecimal laborCost, BigDecimal laborHours, boolean useDefault) {
        var type = serviceTypeRepository.findByName(serviceType)
                .orElseThrow(() -> new UnknownServiceTypeException(serviceType));

        WorkshopService newService = WorkshopService.create(type.getName(), idOS,
                WorkshopService.resolveLabor(laborCost, type.getLaborCost(), useDefault, BigDecimal.ZERO),
                WorkshopService.resolveLabor(laborHours, type.getLaborHours(), useDefault, null),
                LocalDateTime.now());

        return serviceRepository.save(newService);
    }
}
