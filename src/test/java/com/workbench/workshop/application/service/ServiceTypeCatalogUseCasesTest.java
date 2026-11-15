package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Casos de uso do catálogo de tipos de serviço (CRUD). */
class ServiceTypeCatalogUseCasesTest {

    private ServiceTypeRepository repository;
    private final UUID id = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(ServiceTypeRepository.class);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(repository.getById(any())).thenCallRealMethod();
    }

    private ServiceType existing() {
        return new ServiceType(id, "DISCO_E_PASTILHA", "Troca de disco e pastilha", BigDecimal.ZERO, null);
    }

    @Test
    void createNormalizesNameAndKeepsLabor() {
        ServiceType created = new CreateServiceTypeUseCase(repository)
                .execute("  troca de  correia ", "Correia dentada", new BigDecimal("180.00"), new BigDecimal("2"));

        assertEquals("TROCA_DE_CORREIA", created.getName());
        assertEquals(new BigDecimal("180.00"), created.getLaborCost());
    }

    @Test
    void createRejectsDuplicateName() {
        when(repository.existsByName("REVISAO")).thenReturn(true);

        assertThrows(IllegalStateException.class,
                () -> new CreateServiceTypeUseCase(repository).execute("revisao", null, null, null));
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsBlankNameAndNegativeLabor() {
        var useCase = new CreateServiceTypeUseCase(repository);
        assertThrows(IllegalArgumentException.class, () -> useCase.execute(" ", null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("X", null, new BigDecimal("-1"), null));
    }

    @Test
    void updateChangesDescriptionAndLaborButNotName() {
        when(repository.findById(id)).thenReturn(Optional.of(existing()));

        ServiceType updated = new UpdateServiceTypeUseCase(repository)
                .execute(id, "Nova descrição", new BigDecimal("200.00"), new BigDecimal("1.5"));

        assertEquals("DISCO_E_PASTILHA", updated.getName());
        assertEquals("Nova descrição", updated.getDescription());
        assertEquals(new BigDecimal("1.5"), updated.getLaborHours());
    }

    @Test
    void updateLaborCostKeepsDescription() {
        when(repository.findById(id)).thenReturn(Optional.of(existing()));

        ServiceType updated = new UpdateServiceTypeLaborCostUseCase(repository)
                .execute(id, new BigDecimal("200.00"), null);

        assertEquals(new BigDecimal("200.00"), updated.getLaborCost());
        assertEquals("Troca de disco e pastilha", updated.getDescription());
    }

    @Test
    void updateLaborCostRejectsNegative() {
        when(repository.findById(id)).thenReturn(Optional.of(existing()));
        assertThrows(IllegalArgumentException.class,
                () -> new UpdateServiceTypeLaborCostUseCase(repository).execute(id, new BigDecimal("-1"), null));
    }

    @Test
    void deleteRemovesExistingAnd404sMissing() {
        when(repository.findById(id)).thenReturn(Optional.of(existing()));
        new DeleteServiceTypeUseCase(repository).execute(id);
        verify(repository).deleteById(id);

        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> new DeleteServiceTypeUseCase(repository).execute(missing));
    }

    @Test
    void findByIdAndList() {
        when(repository.findById(id)).thenReturn(Optional.of(existing()));
        when(repository.findAll()).thenReturn(java.util.List.of(existing()));

        assertEquals(id, new FindServiceTypeByIdUseCase(repository).execute(id).getId());
        assertEquals(1, new ListServiceTypesUseCase(repository).execute().size());
    }
}
