package com.workbench.workshop.adapter.in.web.servicetype;

import com.workbench.workshop.adapter.in.web.servicetype.ServiceTypeDtos.CreateServiceTypeRequest;
import com.workbench.workshop.adapter.in.web.servicetype.ServiceTypeDtos.ServiceTypeResponse;
import com.workbench.workshop.adapter.in.web.servicetype.ServiceTypeDtos.UpdateLaborCostRequest;
import com.workbench.workshop.adapter.in.web.servicetype.ServiceTypeDtos.UpdateServiceTypeRequest;
import com.workbench.workshop.application.service.CreateServiceTypeUseCase;
import com.workbench.workshop.application.service.DeleteServiceTypeUseCase;
import com.workbench.workshop.application.service.FindServiceTypeByIdUseCase;
import com.workbench.workshop.application.service.ListServiceTypesUseCase;
import com.workbench.workshop.application.service.UpdateServiceTypeLaborCostUseCase;
import com.workbench.workshop.application.service.UpdateServiceTypeUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/service-types")
@RequiredArgsConstructor
@Tag(name = "Service Types", description = "Catálogo de tipos de serviço e preço de mão de obra")
public class ServiceTypeController {

    private final ListServiceTypesUseCase listServiceTypes;
    private final FindServiceTypeByIdUseCase findServiceTypeById;
    private final CreateServiceTypeUseCase createServiceType;
    private final UpdateServiceTypeUseCase updateServiceType;
    private final UpdateServiceTypeLaborCostUseCase updateServiceTypeLaborCost;
    private final DeleteServiceTypeUseCase deleteServiceType;

    @GetMapping
    @Operation(summary = "Listar tipos de serviço (com custo de MO)")
    public ResponseEntity<List<ServiceTypeResponse>> findAll() {
        return ResponseEntity.ok(listServiceTypes.execute().stream().map(ServiceTypeResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar tipo de serviço por ID")
    public ResponseEntity<ServiceTypeResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ServiceTypeResponse.from(findServiceTypeById.execute(id)));
    }

    @PostMapping
    @Operation(summary = "Criar tipo de serviço", description = "O nome é normalizado (maiúsculas, espaços → _) e único.")
    public ResponseEntity<ServiceTypeResponse> create(@Valid @RequestBody CreateServiceTypeRequest request) {
        var created = createServiceType.execute(request.name(), request.description(),
                request.laborCost(), request.laborHours());
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceTypeResponse.from(created));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar tipo de serviço", description = "Descrição e mão de obra; o nome não é alterável.")
    public ResponseEntity<ServiceTypeResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateServiceTypeRequest request) {
        var updated = updateServiceType.execute(id, request.description(), request.laborCost(), request.laborHours());
        return ResponseEntity.ok(ServiceTypeResponse.from(updated));
    }

    @PutMapping("/{id}/labor-cost")
    @Operation(summary = "Atualizar custo de mão de obra de um tipo de serviço")
    public ResponseEntity<ServiceTypeResponse> updateLaborCost(@PathVariable UUID id,
                                                               @Valid @RequestBody UpdateLaborCostRequest request) {
        var updated = updateServiceTypeLaborCost.execute(id, request.laborCost(), request.laborHours());
        return ResponseEntity.ok(ServiceTypeResponse.from(updated));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover tipo de serviço", description = "OS já abertas não são afetadas; novas OS deixam de aceitar o tipo.")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        deleteServiceType.execute(id);
        return ResponseEntity.noContent().build();
    }
}
