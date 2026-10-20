package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import com.workbench.workshop.domain.service.ServiceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ServiceTypeUseCasesTest {

    private ServiceTypeRepository repository;
    private ServiceTypeUseCases useCases;

    @BeforeEach
    void setUp() {
        repository = mock(ServiceTypeRepository.class);
        useCases = new ServiceTypeUseCases(repository);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private ServiceType type(UUID id) {
        return new ServiceType(id, "DISCO_E_PASTILHA", "Troca de disco e pastilha",
                BigDecimal.ZERO, null);
    }

    @Test
    void updateLaborCostSetsValueAndHours() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(type(id)));

        ServiceType result = useCases.updateLaborCost(id, new BigDecimal("200.00"), new BigDecimal("1.5"));

        assertEquals(0, new BigDecimal("200.00").compareTo(result.getLaborCost()));
        assertEquals(0, new BigDecimal("1.5").compareTo(result.getLaborHours()));
    }

    @Test
    void updateLaborCostRejectsNegative() {
        assertThrows(IllegalArgumentException.class,
                () -> useCases.updateLaborCost(UUID.randomUUID(), new BigDecimal("-1"), null));
    }

    @Test
    void updateLaborCostRejectsNegativeHours() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> useCases.updateLaborCost(id, new BigDecimal("10"), new BigDecimal("-1")));
    }

    @Test
    void updateLaborCostThrowsWhenNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class,
                () -> useCases.updateLaborCost(id, new BigDecimal("10"), null));
    }

    @Test
    void findByNameReturnsType() {
        when(repository.findByName("DISCO_E_PASTILHA")).thenReturn(Optional.of(type(UUID.randomUUID())));
        assertEquals("DISCO_E_PASTILHA", useCases.findByName("DISCO_E_PASTILHA").getName());
    }

    @Test
    void findByNameThrowsWhenMissing() {
        when(repository.findByName("X")).thenReturn(Optional.empty());
        assertThrows(NoSuchElementException.class, () -> useCases.findByName("X"));
    }

    @Test
    void findAllDelegates() {
        when(repository.findAll()).thenReturn(List.of());
        assertNotNull(useCases.findAll());
    }
}
