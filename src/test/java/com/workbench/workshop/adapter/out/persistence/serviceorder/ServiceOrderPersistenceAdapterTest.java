package com.workbench.workshop.adapter.out.persistence.serviceorder;

import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.persistence.serviceorder.ServiceOrderEntity;
import com.workbench.workshop.infrastructure.persistence.serviceorder.ServiceOrderJpaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOrderPersistenceAdapterTest {

    @Mock private ServiceOrderJpaRepository jpaRepository;
    @InjectMocks private ServiceOrderPersistenceAdapter adapter;

    private ServiceOrderEntity entity(UUID id, String status) {
        return ServiceOrderEntity.builder()
                .id(id)
                .serviceTypeName("[TROCA_OLEO]")
                .serviceStatus(status)
                .listService(List.of("TROCA_OLEO"))
                .cpfCnpj("52998224725")
                .placaVeiculo("ABC1234")
                .rejectionReason(null)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void saveMapsDomainToEntityAndBack() {
        ServiceOrder order = ServiceOrder.create("52998224725", "ABC1234", List.of("TROCA_OLEO"));
        when(jpaRepository.save(any(ServiceOrderEntity.class)))
                .thenAnswer(i -> {
                    ServiceOrderEntity e = i.getArgument(0);
                    e.setCreatedAt(LocalDateTime.now());
                    e.setUpdatedAt(LocalDateTime.now());
                    return e;
                });

        ServiceOrder saved = adapter.save(order);

        assertEquals(order.getId(), saved.getId());
        assertEquals("ABC1234", saved.getPlacaVeiculo());
        assertEquals(OrderServiceStatusEnum.RECEBIDA, saved.getStatus());
    }

    @Test
    void findByIdReturnsDomain() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.of(entity(id, "RECEBIDA")));

        Optional<ServiceOrder> result = adapter.findById(id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }

    @Test
    void findByIdEmptyWhenMissing() {
        UUID id = UUID.randomUUID();
        when(jpaRepository.findById(id)).thenReturn(Optional.empty());

        assertTrue(adapter.findById(id).isEmpty());
    }

    @Test
    void findAllMapsList() {
        when(jpaRepository.findAll()).thenReturn(List.of(entity(UUID.randomUUID(), "RECEBIDA")));

        assertEquals(1, adapter.findAll().size());
    }

    @Test
    void findByCpfCnpjMapsList() {
        when(jpaRepository.findByCpfCnpj("52998224725"))
                .thenReturn(List.of(entity(UUID.randomUUID(), "EM_DIAGNOSTICO")));

        List<ServiceOrder> result = adapter.findByCpfCnpj("52998224725");

        assertEquals(1, result.size());
        assertEquals(OrderServiceStatusEnum.EM_DIAGNOSTICO, result.get(0).getStatus());
    }

    @Test
    void findActiveOrdersSortedMapsList() {
        when(jpaRepository.findActiveOrdersSorted())
                .thenReturn(List.of(entity(UUID.randomUUID(), "RECEBIDA")));

        assertEquals(1, adapter.findActiveOrdersSorted().size());
    }
}
