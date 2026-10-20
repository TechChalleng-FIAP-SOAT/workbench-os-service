package com.workbench.workshop.application.saga.port.out;

import com.workbench.workshop.domain.saga.SagaState;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SagaStateRepository {
    SagaState save(SagaState sagaState);
    Optional<SagaState> findByServiceOrderId(UUID serviceOrderId);

    /**
     * Sagas com comando pendente cujo último envio é anterior ao limite informado
     * (candidatas a reenvio/compensação por timeout).
     */
    List<SagaState> findPendingOlderThan(LocalDateTime threshold);
}
