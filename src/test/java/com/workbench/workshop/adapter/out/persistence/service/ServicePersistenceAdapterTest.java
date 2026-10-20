package com.workbench.workshop.adapter.out.persistence.service;

import com.workbench.workshop.domain.service.ServiceType;
import com.workbench.workshop.domain.service.WorkshopService;
import com.workbench.workshop.infrastructure.persistence.service.ServiceEntity;
import com.workbench.workshop.infrastructure.persistence.service.ServiceJpaRepository;
import com.workbench.workshop.infrastructure.persistence.service.ServiceTypeEntity;
import com.workbench.workshop.infrastructure.persistence.service.ServiceTypeJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServicePersistenceAdapterTest {

    @Mock private ServiceJpaRepository serviceJpaRepository;
    @Mock private ServiceTypeJpaRepository serviceTypeJpaRepository;
    @InjectMocks private ServicePersistenceAdapter serviceAdapter;
    @InjectMocks private ServiceTypePersistenceAdapter typeAdapter;

    @Test
    void saveServiceMapsBothWays() {
        UUID idOS = UUID.randomUUID();
        WorkshopService service = new WorkshopService(null, "Pintura", idOS, List.of());
        when(serviceJpaRepository.save(any(ServiceEntity.class)))
                .thenAnswer(i -> {
                    ServiceEntity e = i.getArgument(0);
                    e.setId(UUID.randomUUID());
                    return e;
                });

        WorkshopService saved = serviceAdapter.save(service);

        assertNotNull(saved.getId());
        assertEquals("Pintura", saved.getServiceTypeName());
        assertEquals(idOS, saved.getIdOS());
    }

    @Test
    void findServiceByIdReturnsDomain() {
        UUID id = UUID.randomUUID();
        ServiceEntity e = new ServiceEntity(id, "Mecanica", UUID.randomUUID(), List.of(),
                java.math.BigDecimal.ZERO, null);
        when(serviceJpaRepository.findById(id)).thenReturn(Optional.of(e));

        assertTrue(serviceAdapter.findById(id).isPresent());
    }

    @Test
    void findByIdOSMapsList() {
        UUID idOS = UUID.randomUUID();
        when(serviceJpaRepository.findByIdOS(idOS))
                .thenReturn(List.of(new ServiceEntity(UUID.randomUUID(), "Pintura", idOS, List.of(),
                        java.math.BigDecimal.ZERO, null)));

        assertEquals(1, serviceAdapter.findByIdOS(idOS).size());
    }

    @Test
    void findAllServicesMapsList() {
        when(serviceJpaRepository.findAll())
                .thenReturn(List.of(new ServiceEntity(UUID.randomUUID(), "Pintura", UUID.randomUUID(), List.of(),
                        java.math.BigDecimal.ZERO, null)));

        assertEquals(1, serviceAdapter.findAll().size());
    }

    @Test
    void findTypeByNameReturnsDomain() {
        ServiceTypeEntity e = new ServiceTypeEntity(UUID.randomUUID(), "TROCA_OLEO", "desc",
                java.math.BigDecimal.ZERO, null);
        when(serviceTypeJpaRepository.findByName("TROCA_OLEO")).thenReturn(Optional.of(e));

        Optional<ServiceType> result = typeAdapter.findByName("TROCA_OLEO");

        assertTrue(result.isPresent());
        assertEquals("TROCA_OLEO", result.get().getName());
    }

    @Test
    void findAllTypesMapsList() {
        when(serviceTypeJpaRepository.findAll())
                .thenReturn(List.of(new ServiceTypeEntity(UUID.randomUUID(), "TROCA_OLEO", "desc",
                        java.math.BigDecimal.ZERO, null)));

        assertEquals(1, typeAdapter.findAll().size());
    }
}
