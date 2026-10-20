package com.workbench.workshop.infrastructure.persistence.service;

import jakarta.persistence.*;
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
@Entity
@Table(name = "service_type")
public class ServiceTypeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column
    private String description;

    @Column(name = "labor_cost", nullable = false)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "labor_hours")
    private BigDecimal laborHours;
}
