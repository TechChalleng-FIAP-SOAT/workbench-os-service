package com.workbench.workshop.adapter.out.persistence.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import com.workbench.workshop.infrastructure.persistence.service.ServiceTypeEntity;
import com.workbench.workshop.infrastructure.persistence.service.ServiceTypeJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServiceTypePersistenceAdapter implements ServiceTypeRepository {

    private final ServiceTypeJpaRepository serviceTypeJpaRepository;

    @Override
    public Optional<ServiceType> findByName(String name) {
        return serviceTypeJpaRepository.findByName(name).map(this::toDomain);
    }

    @Override
    public Optional<ServiceType> findById(UUID id) {
        return serviceTypeJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<ServiceType> findAll() {
        return serviceTypeJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    public ServiceType save(ServiceType serviceType) {
        ServiceTypeEntity entity = (serviceType.getId() != null)
                ? serviceTypeJpaRepository.findById(serviceType.getId()).orElseGet(ServiceTypeEntity::new)
                : new ServiceTypeEntity();
        entity.setName(serviceType.getName());
        entity.setDescription(serviceType.getDescription());
        entity.setLaborCost(serviceType.getLaborCost());
        entity.setLaborHours(serviceType.getLaborHours());
        return toDomain(serviceTypeJpaRepository.save(entity));
    }

    private ServiceType toDomain(ServiceTypeEntity entity) {
        return new ServiceType(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getLaborCost(), entity.getLaborHours());
    }
}
