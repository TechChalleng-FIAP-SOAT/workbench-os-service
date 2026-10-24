package com.workbench.workshop.domain.monitoring;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tempo médio de execução de um tipo de serviço (paridade com o monólito). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAverageTime {
    private String serviceTypeName;
    private double averageTime;
}
