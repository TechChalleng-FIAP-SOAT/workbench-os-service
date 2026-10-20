package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.infrastructure.config.UpperCase;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "Create Order request payload.")
public class CreateOrderRequest {
    @Schema(description = "Client CPF or CNPJ.", example = "52998224725")
    private String cpfCnpj;

    @UpperCase
    @Schema(description = "Vehicle plate.", example = "ABC-1234")
    private String placaVeiculo;

    @UpperCase
    @Schema(description = "Service types registered in the system.", example = "[\"TROCA_OLEO\", \"ALINHAMENTO\"]")
    private List<String> serviceTypes;
}
