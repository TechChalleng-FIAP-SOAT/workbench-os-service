package com.workbench.workshop.domain.service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ServiceType {

    private UUID id;
    private String name;
    private String description;

    /** Custo fixo de mão de obra deste tipo de serviço (editável via API). */
    private BigDecimal laborCost = BigDecimal.ZERO;

    /** Tempo estimado de MO em horas (coluna "Tempo MO" do orçamento real); opcional. */
    private BigDecimal laborHours;
}
