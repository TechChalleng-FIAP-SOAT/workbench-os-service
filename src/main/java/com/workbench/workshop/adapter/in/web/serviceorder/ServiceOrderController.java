package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.application.Requester;
import com.workbench.workshop.application.serviceorder.CreateOrderUseCase;
import com.workbench.workshop.application.serviceorder.DecideOrderUseCase;
import com.workbench.workshop.application.serviceorder.DiagnoseOrderUseCase;
import com.workbench.workshop.application.serviceorder.FindOrderByIdUseCase;
import com.workbench.workshop.application.serviceorder.FindOrderSagaUseCase;
import com.workbench.workshop.application.serviceorder.FindOrdersByDocumentUseCase;
import com.workbench.workshop.application.serviceorder.ListOrderServicesUseCase;
import com.workbench.workshop.application.serviceorder.ListOrdersUseCase;
import com.workbench.workshop.application.serviceorder.RetryPaymentUseCase;
import com.workbench.workshop.application.serviceorder.UpdateOrderUseCase;
import com.workbench.workshop.application.serviceorder.UpdateServiceStatusUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Service Orders", description = "Gestao de Ordens de Servico")
public class ServiceOrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final UpdateOrderUseCase updateOrderUseCase;
    private final DiagnoseOrderUseCase diagnoseOrderUseCase;
    private final DecideOrderUseCase decideOrderUseCase;
    private final FindOrderByIdUseCase findOrderByIdUseCase;
    private final ListOrdersUseCase listOrdersUseCase;
    private final FindOrdersByDocumentUseCase findOrdersByDocumentUseCase;
    private final RetryPaymentUseCase retryPaymentUseCase;
    private final ListOrderServicesUseCase listOrderServicesUseCase;
    private final UpdateServiceStatusUseCase updateServiceStatusUseCase;
    private final FindOrderSagaUseCase findOrderSagaUseCase;

    @PostMapping
    @Operation(summary = "Criar ordem de servico", description = "Cria uma nova OS associando cliente (CPF/CNPJ), veiculo (placa) e tipos de servico.")
    public ResponseEntity<ServiceOrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        var order = createOrderUseCase.execute(request.getCpfCnpj(), request.getPlacaVeiculo(), request.getServiceTypes());
        var response = ServiceOrderResponse.from(order);
        log.info("Service order created: id={}, status={}", response.id(), response.serviceStatus());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar OS ativas", description = "Retorna OS ativas (exclui FINALIZADA/ENTREGUE), ordenadas por prioridade de status e data de criacao.")
    public ResponseEntity<List<ServiceOrderResponse>> listOrders() {
        return ResponseEntity.ok(listOrdersUseCase.execute().stream().map(ServiceOrderResponse::from).toList());
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar OS por ID", description = "Retorna uma OS pelo seu UUID.")
    public ResponseEntity<ServiceOrderResponse> findOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(ServiceOrderResponse.from(findOrderByIdUseCase.execute(id)));
    }

    @GetMapping("/document/{cpfCnpj}")
    @Operation(summary = "Buscar OS por documento", description = "Retorna todas as OS vinculadas a um cliente pelo CPF ou CNPJ (historico completo). Cliente so consulta o proprio documento.")
    public ResponseEntity<List<ServiceOrderResponse>> findByDocument(@PathVariable String cpfCnpj,
                                                                     Authentication authentication) {
        return ResponseEntity.ok(findOrdersByDocumentUseCase.execute(cpfCnpj, requester(authentication)).stream()
                .map(ServiceOrderResponse::from).toList());
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar status da OS", description = "Atualiza o status da OS. Valida transicoes permitidas. Retorna 204 No Content.")
    public ResponseEntity<Void> updateOrder(@PathVariable UUID id, @RequestBody UpdateOrderRequest request) {
        updateOrderUseCase.execute(id, request.getStatus());
        log.info("Service order status updated: id={}, newStatus={}", id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/diagnosis")
    @Operation(summary = "Registrar diagnostico da OS",
            description = "Informa as pecas a reservar e a mao de obra por servico. Transita a OS para "
                    + "EM_DIAGNOSTICO e inicia a Saga (reserva de estoque).")
    public ResponseEntity<ServiceOrderResponse> diagnose(@PathVariable UUID id, @RequestBody DiagnoseOrderRequest request) {
        var order = diagnoseOrderUseCase.execute(id, request.reserveItems(), request.labor());
        log.info("Service order diagnosed: id={}, status={}", order.getId(), order.getServiceStatus());
        return ResponseEntity.ok(ServiceOrderResponse.from(order));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Decidir orcamento (aprovar/recusar)",
            description = "Decisao do cliente sobre o orcamento. APPROVED confirma a reserva (baixa de "
                    + "estoque) e a OS vai para APROVADO; REJECTED libera a reserva e a OS vai para RECUSADA.")
    public ResponseEntity<Void> decide(@PathVariable UUID id, @RequestBody DecideOrderRequest request,
                                       Authentication authentication) {
        decideOrderUseCase.execute(id, request.decision(), request.reason(), requester(authentication));
        log.info("Service order budget decided: id={}, decision={}", id, request.decision());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/payment-retry")
    @Operation(summary = "Gerar nova cobrança após pagamento recusado",
            description = "Quando o pagamento da OS foi recusado/cancelado, gera um novo link de pagamento "
                    + "(enviado por e-mail). Cliente só pode pedir para a própria OS.")
    public ResponseEntity<Void> retryPayment(@PathVariable UUID id, Authentication authentication) {
        retryPaymentUseCase.execute(id, requester(authentication));
        log.info("Service order payment retry requested: id={}", id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{id}/saga")
    @Operation(summary = "Estado da Saga da OS", description = "Passo atual, passos concluídos e o motivo quando falhou (ex.: estoque insuficiente). Com a Saga em FALHOU, um novo POST /diagnosis a recomeça.")
    public ResponseEntity<OrderSagaResponse> findSaga(@PathVariable UUID id) {
        return ResponseEntity.ok(OrderSagaResponse.from(findOrderSagaUseCase.execute(id)));
    }

    @GetMapping("/{id}/services")
    @Operation(summary = "Listar serviços da OS", description = "Status de execução (TO_DO/DOING/DONE) e histórico de cada serviço.")
    public ResponseEntity<List<OrderServiceResponse>> listServices(@PathVariable UUID id) {
        return ResponseEntity.ok(listOrderServicesUseCase.execute(id).stream().map(OrderServiceResponse::from).toList());
    }

    @PatchMapping("/{id}/services/{serviceId}/status")
    @Operation(summary = "Avançar a execução de um serviço da OS",
            description = "TO_DO → DOING → DONE, com a OS em EM_EXECUCAO. Ao concluir o último serviço, a OS vai "
                    + "automaticamente para FINALIZADA. Os instantes alimentam o relatório de tempo médio (/monitoring).")
    public ResponseEntity<OrderServiceResponse> updateServiceStatus(@PathVariable UUID id, @PathVariable UUID serviceId,
                                                                    @Valid @RequestBody UpdateServiceStatusRequest request) {
        var service = updateServiceStatusUseCase.execute(id, serviceId, request.status());
        log.info("Service order item status updated: os={}, service={}, status={}", id, serviceId, request.status());
        return ResponseEntity.ok(OrderServiceResponse.from(service));
    }

    /** Sem autenticação (teste standalone) vale como staff; a SecurityConfig barra o acesso real. */
    private static Requester requester(Authentication authentication) {
        if (authentication == null) {
            return Requester.staff(null);
        }
        boolean client = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_CLIENT".equals(a.getAuthority()));
        return new Requester(authentication.getName(), client);
    }
}
