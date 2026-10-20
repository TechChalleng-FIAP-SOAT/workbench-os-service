package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceRepository;
import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.ServiceType;
import com.workbench.workshop.domain.service.WorkshopService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateServiceUseCaseTest {

    @Mock private ServiceRepository serviceRepository;
    @Mock private ServiceTypeRepository serviceTypeRepository;
    @InjectMocks private CreateServiceUseCase useCase;

    @Test
    void createsServiceSuccessfully() {
        UUID idOS = UUID.randomUUID();
        ServiceType type = new ServiceType(UUID.randomUUID(), "Troca de Óleo", "Troca completa",
                java.math.BigDecimal.ZERO, null);
        when(serviceTypeRepository.findByName("Troca de Óleo")).thenReturn(Optional.of(type));
        when(serviceRepository.save(any(WorkshopService.class))).thenAnswer(i -> i.getArgument(0));

        WorkshopService result = useCase.execute("Troca de Óleo", idOS);

        assertNotNull(result);
        assertEquals("Troca de Óleo", result.getServiceTypeName());
        assertEquals(idOS, result.getIdOS());
        assertEquals(1, result.getServiceStatus().size());
        assertEquals(ServiceStatusEnum.TO_DO, result.getServiceStatus().get(0).getStatus());
        assertNotNull(result.getServiceStatus().get(0).getChangedAt());
        verify(serviceRepository).save(any(WorkshopService.class));
    }

    @Test
    void throwsWhenServiceTypeNotFound() {
        UUID idOS = UUID.randomUUID();
        when(serviceTypeRepository.findByName("Inexistente")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> useCase.execute("Inexistente", idOS));

        assertTrue(ex.getMessage().contains("Tipo de serviço não encontrado"));
        verify(serviceRepository, never()).save(any());
    }
}
