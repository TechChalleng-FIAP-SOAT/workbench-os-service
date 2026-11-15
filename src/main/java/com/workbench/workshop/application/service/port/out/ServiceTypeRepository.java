package com.workbench.workshop.application.service.port.out;

import com.workbench.workshop.domain.service.ServiceType;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public interface ServiceTypeRepository {
    Optional<ServiceType> findByName(String name);
    Optional<ServiceType> findById(UUID id);
    List<ServiceType> findAll();
    ServiceType save(ServiceType serviceType);
    boolean existsByName(String name);
    void deleteById(UUID id);

    /** Tipo pelo id ou {@link NoSuchElementException} (404). */
    default ServiceType getById(UUID id) {
        return findById(id).orElseThrow(() -> new NoSuchElementException("Tipo de serviço não encontrado: " + id));
    }
}
