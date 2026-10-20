package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.application.serviceorder.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
        var responses = listOrdersUseCase.execute().stream().map(ServiceOrderResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/id/{id}")
    @Operation(summary = "Buscar OS por ID", description = "Retorna uma OS pelo seu UUID.")
    public ResponseEntity<ServiceOrderResponse> findOrderById(@PathVariable UUID id) {
        return ResponseEntity.ok(ServiceOrderResponse.from(findOrderByIdUseCase.execute(id)));
    }

    @GetMapping("/document/{cpfCnpj}")
    @Operation(summary = "Buscar OS por documento", description = "Retorna todas as OS vinculadas a um cliente pelo CPF ou CNPJ (historico completo).")
    public ResponseEntity<List<ServiceOrderResponse>> findByDocument(@PathVariable String cpfCnpj) {
        var responses = findOrdersByDocumentUseCase.execute(cpfCnpj).stream()
                .map(ServiceOrderResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Atualizar status da OS", description = "Atualiza o status da OS. Valida transicoes permitidas. Retorna 204 No Content.")
    public ResponseEntity<Void> updateOrder(
            @PathVariable UUID id, @RequestBody UpdateOrderRequest request) {
        updateOrderUseCase.execute(id, request.getStatus());
        log.info("Service order status updated: id={}, newStatus={}", id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/diagnosis")
    @Operation(summary = "Registrar diagnostico da OS",
            description = "Informa as pecas a reservar e a mao de obra por servico. Transita a OS para "
                    + "EM_DIAGNOSTICO e inicia a Saga (reserva de estoque).")
    public ResponseEntity<ServiceOrderResponse> diagnose(
            @PathVariable UUID id, @RequestBody DiagnoseOrderRequest request) {
        var parts = request.parts() == null ? List.<DiagnoseOrderUseCase.PartItem>of()
                : request.parts().stream()
                    .map(p -> new DiagnoseOrderUseCase.PartItem(p.productId(), p.quantity()))
                    .toList();
        var services = request.services() == null ? List.<DiagnoseOrderUseCase.ServiceLabor>of()
                : request.services().stream()
                    .map(s -> new DiagnoseOrderUseCase.ServiceLabor(
                            s.serviceType(), s.laborCost(), s.laborHours(),
                            Boolean.TRUE.equals(s.useDefault())))
                    .toList();
        var order = diagnoseOrderUseCase.execute(id, parts, services);
        log.info("Service order diagnosed: id={}, status={}", order.getId(), order.getServiceStatus());
        return ResponseEntity.ok(ServiceOrderResponse.from(order));
    }

    @PostMapping("/{id}/decision")
    @Operation(summary = "Decidir orcamento (aprovar/recusar)",
            description = "Decisao do cliente sobre o orcamento. APPROVED confirma a reserva (baixa de "
                    + "estoque) e a OS vai para APROVADO; REJECTED libera a reserva e a OS vai para RECUSADA.")
    public ResponseEntity<Void> decide(
            @PathVariable UUID id, @RequestBody DecideOrderRequest request) {
        decideOrderUseCase.execute(id, request.decision(), request.reason());
        log.info("Service order budget decided: id={}, decision={}", id, request.decision());
        return ResponseEntity.noContent().build();
    }
}
