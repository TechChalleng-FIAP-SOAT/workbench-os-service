package com.workbench.workshop.domain.monitoring;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.NoArgsConstructor;

/** Tempo médio de execução de um tipo de serviço (paridade com o monólito). */
@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ServiceAverageTime {
    private String serviceTypeName;
    private double averageTime;
}
