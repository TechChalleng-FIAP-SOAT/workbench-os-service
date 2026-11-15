package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lista o catálogo de tipos de serviço (com o custo de mão de obra). */
@Service
@RequiredArgsConstructor
public class ListServiceTypesUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional(readOnly = true)
    public List<ServiceType> execute() {
        return serviceTypeRepository.findAll();
    }
}
