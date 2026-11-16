package com.workbench.workshop.application;

import com.workbench.workshop.domain.serviceorder.ServiceOrder;

/**
 * Quem fez a requisição: o {@code subject} do JWT (CPF/CNPJ para cliente, e-mail para
 * staff) e se é um token de cliente ({@code ROLE_CLIENT}). Cliente só acessa as OS do
 * próprio documento; staff acessa todas.
 */
public record Requester(String subject, boolean client) {

    public static Requester staff(String subject) {
        return new Requester(subject, false);
    }

    /** True quando pode acessar dados do cliente dono deste documento. */
    public boolean canAccess(String cpfCnpj) {
        return !client || (subject != null
                && ServiceOrder.normalizeDocument(subject).equals(ServiceOrder.normalizeDocument(cpfCnpj)));
    }

    public void requireAccessTo(String cpfCnpj) {
        if (!canAccess(cpfCnpj)) {
            throw new SecurityException("Cliente não autorizado a acessar dados de outro cliente.");
        }
    }
}
