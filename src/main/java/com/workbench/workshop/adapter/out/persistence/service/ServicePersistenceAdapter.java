package com.workbench.workshop.adapter.out.persistence.service;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.infrastructure.persistence.service.ServiceEntity;
import com.workbench.workshop.infrastructure.persistence.service.ServiceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ServicePersistenceAdapter implements ServiceRepository {

    private final ServiceJpaRepository serviceJpaRepository;

    @Override
    public WorkshopService save(WorkshopService service) {
        ServiceEntity entity = toEntity(service);
        ServiceEntity saved = serviceJpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<WorkshopService> findById(UUID id) {
        return serviceJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<WorkshopService> findByIdOS(UUID idOS) {
        return serviceJpaRepository.findByIdOS(idOS).stream().map(this::toDomain).toList();
    }

    @Override
    public List<WorkshopService> findAll() {
        return serviceJpaRepository.findAll().stream().map(this::toDomain).toList();
    }

    private WorkshopService toDomain(ServiceEntity entity) {
        WorkshopService service = new WorkshopService(
                entity.getId(), entity.getServiceTypeName(), entity.getIdOS(), entity.getServiceStatus());
        service.setLaborCost(entity.getLaborCost() == null ? java.math.BigDecimal.ZERO : entity.getLaborCost());
        service.setLaborHours(entity.getLaborHours());
        return service;
    }

    private ServiceEntity toEntity(WorkshopService service) {
        ServiceEntity entity = new ServiceEntity();
        entity.setId(service.getId());
        entity.setServiceTypeName(service.getServiceTypeName());
        entity.setIdOS(service.getIdOS());
        entity.setServiceStatus(service.getServiceStatus());
        entity.setLaborCost(service.getLaborCost() == null ? java.math.BigDecimal.ZERO : service.getLaborCost());
        entity.setLaborHours(service.getLaborHours());
        return entity;
    }
}
