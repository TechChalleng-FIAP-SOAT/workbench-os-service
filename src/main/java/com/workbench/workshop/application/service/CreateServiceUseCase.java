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

    /**
     * Cria o serviço resolvendo a mão de obra conforme a regra de default (ADR-0005):
     * <ul>
     *   <li>{@code useDefault=false}: usa o valor do campo; se nulo → 0.</li>
     *   <li>{@code useDefault=true} e campo nulo: usa o default do catálogo
     *       ({@code ServiceType}).</li>
     *   <li>{@code useDefault=true} e campo preenchido: o campo prevalece.</li>
     * </ul>
     */
    public WorkshopService execute(String serviceType, UUID idOS,
                                   BigDecimal laborCost, BigDecimal laborHours, boolean useDefault) {
        var type = serviceTypeRepository.findByName(serviceType)
                .orElseThrow(() -> new RuntimeException("Tipo de serviço não encontrado na base de Serviços."));

        WorkshopService newService = new WorkshopService();
        newService.setServiceTypeName(type.getName());
        newService.setIdOS(idOS);
        newService.setServiceStatus(List.of(new Status(ServiceStatusEnum.TO_DO, LocalDateTime.now())));
        newService.setLaborCost(resolve(laborCost, type.getLaborCost(), useDefault, BigDecimal.ZERO));
        newService.setLaborHours(resolve(laborHours, type.getLaborHours(), useDefault, null));

        return serviceRepository.save(newService);
    }

    /** Precedência: campo preenchido vence; useDefault puxa o catálogo quando o campo é nulo. */
    private BigDecimal resolve(BigDecimal field, BigDecimal catalogDefault, boolean useDefault, BigDecimal fallback) {
        if (field != null) {
            return field;
        }
        return useDefault ? catalogDefault : fallback;
    }
}
