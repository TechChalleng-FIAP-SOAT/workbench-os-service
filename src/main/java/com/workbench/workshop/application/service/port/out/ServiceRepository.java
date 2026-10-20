package com.workbench.workshop.application.service.port.out;

import com.workbench.workshop.domain.service.WorkshopService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceRepository {
    WorkshopService save(WorkshopService service);
    Optional<WorkshopService> findById(UUID id);
    List<WorkshopService> findByIdOS(UUID idOS);
    List<WorkshopService> findAll();
}
