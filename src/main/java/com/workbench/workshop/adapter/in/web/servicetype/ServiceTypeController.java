package com.workbench.workshop.adapter.in.web.servicetype;

import com.workbench.workshop.application.service.ServiceTypeUseCases;
import com.workbench.workshop.domain.service.ServiceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/service-types")
@RequiredArgsConstructor
@Tag(name = "Service Types", description = "Catálogo de tipos de serviço e preço de mão de obra")
public class ServiceTypeController {

    private final ServiceTypeUseCases useCases;

    @GetMapping
    @Operation(summary = "Listar tipos de serviço (com custo de MO)")
    public ResponseEntity<List<ServiceTypeResponse>> findAll() {
        return ResponseEntity.ok(useCases.findAll().stream().map(ServiceTypeResponse::from).toList());
    }

    @PutMapping("/{id}/labor-cost")
    @Operation(summary = "Atualizar custo de mão de obra de um tipo de serviço")
    public ResponseEntity<ServiceTypeResponse> updateLaborCost(
            @PathVariable UUID id, @RequestBody UpdateLaborCostRequest request) {
        ServiceType updated = useCases.updateLaborCost(id, request.laborCost(), request.laborHours());
        return ResponseEntity.ok(ServiceTypeResponse.from(updated));
    }

    public record UpdateLaborCostRequest(BigDecimal laborCost, BigDecimal laborHours) {}

    public record ServiceTypeResponse(UUID id, String name, String description,
                                      BigDecimal laborCost, BigDecimal laborHours) {
        static ServiceTypeResponse from(ServiceType t) {
            return new ServiceTypeResponse(t.getId(), t.getName(), t.getDescription(),
                    t.getLaborCost(), t.getLaborHours());
        }
    }
}
