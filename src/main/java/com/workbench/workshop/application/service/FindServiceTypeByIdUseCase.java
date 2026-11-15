package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Tipo de serviço pelo id (404 se não existir). */
@Service
@RequiredArgsConstructor
public class FindServiceTypeByIdUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public ServiceType execute(UUID id) {
        return serviceTypeRepository.getById(id);
    }
}
