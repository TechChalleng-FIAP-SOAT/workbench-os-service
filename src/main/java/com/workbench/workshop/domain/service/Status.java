package com.workbench.workshop.domain.service;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class Status {

    private ServiceStatusEnum status = ServiceStatusEnum.TO_DO;
    private LocalDateTime changedAt;
}
