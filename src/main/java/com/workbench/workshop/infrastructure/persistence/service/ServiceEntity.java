package com.workbench.workshop.infrastructure.persistence.service;

import com.workbench.workshop.domain.service.ServiceStatusEnum;
import com.workbench.workshop.domain.service.Status;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "service")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "service_type_name", nullable = false)
    private String serviceTypeName;

    @Column(name = "id_os", nullable = false)
    private UUID idOS;

    @Column(name = "service_status", nullable = false)
    @Convert(converter = ServiceStatusConverter.class)
    private List<Status> serviceStatus = List.of(
            new Status(ServiceStatusEnum.TO_DO, LocalDateTime.now())
    );

    @Column(name = "labor_cost", nullable = false)
    private BigDecimal laborCost = BigDecimal.ZERO;

    @Column(name = "labor_hours")
    private BigDecimal laborHours;
}
