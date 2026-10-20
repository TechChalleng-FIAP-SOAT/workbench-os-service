package com.workbench.workshop.domain.saga;

/**
 * Passo atual da Saga da OS (coordenação distribuída — distinto do status de
 * negócio da OS). Ver ADR-0003/0005.
 */
public enum SagaStep {
    INICIADA,
    AGUARDANDO_RESERVA,      // comando de reserva enviado ao Execution
    AGUARDANDO_ORCAMENTO,    // comando de orçamento enviado ao Billing
    AGUARDANDO_APROVACAO,    // pausa humana: cliente aprova/recusa o orçamento
    AGUARDANDO_PAGAMENTO,    // cobrança criada; aguardando confirmação (webhook)
    CONCLUIDA,
    COMPENSANDO,
    FALHOU
}
