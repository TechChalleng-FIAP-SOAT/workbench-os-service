package com.workbench.workshop.application.serviceorder;

import com.workbench.workshop.application.Requester;
import com.workbench.workshop.application.serviceorder.port.out.ServiceOrderRepository;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FindOrdersByDocumentUseCase {

    private final ServiceOrderRepository serviceOrderRepository;

    /** OS do documento; cliente só consulta o próprio documento (403 caso contrário). */
    @Transactional(readOnly = true)
    public List<ServiceOrder> execute(String cpfCnpj, Requester requester) {
        requester.requireAccessTo(cpfCnpj);
        return serviceOrderRepository.findByCpfCnpj(ServiceOrder.normalizeDocument(cpfCnpj));
    }
}
