package com.workbench.workshop.domain.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tipo de serviço do catálogo (ex.: TROCA_OLEO), com o custo e o tempo de mão de obra
 * usados como default no diagnóstico.
 *
 * <p>O {@code name} é a chave de negócio: a OS e seus serviços guardam o nome, e o
 * diagnóstico encontra o default por ele — por isso o nome é imutável depois de criado.
 */
@Getter
@AllArgsConstructor
public class ServiceType {

    private UUID id;
    private String name;
    private String description;

    /** Custo fixo de mão de obra deste tipo de serviço (editável via API). */
    private BigDecimal laborCost = BigDecimal.ZERO;

    /** Tempo estimado de MO em horas (coluna "Tempo MO" do orçamento real); opcional. */
    private BigDecimal laborHours;

    /** Novo tipo: nome normalizado (maiúsculas, espaços → {@code _}) e valores de MO válidos. */
    public static ServiceType create(String name, String description, BigDecimal laborCost, BigDecimal laborHours) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Nome do tipo de serviço é obrigatório.");
        }
        ServiceType type = new ServiceType(null, normalizeName(name), description, BigDecimal.ZERO, null);
        type.changeLabor(laborCost == null ? BigDecimal.ZERO : laborCost, laborHours);
        return type;
    }

    /** Altera descrição e mão de obra; o nome não muda. */
    public void update(String newDescription, BigDecimal newLaborCost, BigDecimal newLaborHours) {
        this.description = newDescription;
        changeLabor(newLaborCost, newLaborHours);
    }

    /** Altera só o custo (obrigatório, ≥ 0) e as horas (opcional, ≥ 0) de mão de obra. */
    public void changeLabor(BigDecimal newLaborCost, BigDecimal newLaborHours) {
        if (newLaborCost == null || newLaborCost.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Custo de mão de obra deve ser maior ou igual a zero.");
        }
        if (newLaborHours != null && newLaborHours.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Horas de mão de obra devem ser maiores ou iguais a zero.");
        }
        this.laborCost = newLaborCost;
        this.laborHours = newLaborHours;
    }

    /** Formato único do nome: {@code "troca de óleo"} → {@code "TROCA_DE_ÓLEO"}. */
    public static String normalizeName(String name) {
        return name.strip().replaceAll("\\s+", "_").toUpperCase();
    }
}
