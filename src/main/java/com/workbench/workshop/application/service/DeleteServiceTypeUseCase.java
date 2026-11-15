package com.workbench.workshop.application.service;

import com.workbench.workshop.application.service.port.out.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Remove um tipo do catálogo. OS já abertas não são afetadas (cada serviço da OS guarda
 * o nome do tipo e o próprio custo de mão de obra); novas OS deixam de aceitar o tipo.
 */
@Service
@RequiredArgsConstructor
public class DeleteServiceTypeUseCase {

    private final ServiceTypeRepository serviceTypeRepository;

    @Transactional
    public void execute(UUID id) {
        serviceTypeRepository.getById(id); // 404 se não existir
        serviceTypeRepository.deleteById(id);
    }
}
