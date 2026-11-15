package com.workbench.workshop.application.service;

/** A OS (ou o diagnóstico) pediu um tipo de serviço que não está no catálogo. */
public class UnknownServiceTypeException extends RuntimeException {

    public UnknownServiceTypeException(String serviceTypeName) {
        super("Tipo de serviço não encontrado na base de Serviços: " + serviceTypeName);
    }
}
