package com.workbench.workshop.application.serviceorder.port.out;

import java.util.Optional;

/**
 * Porta de saída para obter dados de contato do cliente (e-mail) a partir do
 * documento (CPF/CNPJ). O OS Service não acessa o banco do Customer (ADR-0003):
 * resolve o contato por REST. O fallback no-op permite rodar/testar sem o
 * Customer Service no ar.
 */
public interface ClientContactPort {

    /**
     * Retorna o e-mail do cliente dono do documento, se disponível.
     * Vazio quando o cliente não tem e-mail cadastrado ou a consulta não resolve.
     */
    Optional<String> findEmailByDocument(String cpfCnpj);
}
