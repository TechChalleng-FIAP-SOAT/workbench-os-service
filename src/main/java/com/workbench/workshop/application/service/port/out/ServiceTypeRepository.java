package com.workbench.workshop.application.service.port.out;

import com.workbench.workshop.domain.service.ServiceType;

import java.util.List;
import java.util.Optional;

public interface ServiceTypeRepository {
    Optional<ServiceType> findByName(String name);
    Optional<ServiceType> findById(java.util.UUID id);
    List<ServiceType> findAll();
    ServiceType save(ServiceType serviceType);
}
