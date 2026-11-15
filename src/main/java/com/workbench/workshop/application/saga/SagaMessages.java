package com.workbench.workshop.application.saga;

/**
 * Nomes dos comandos e eventos da Saga (contrato com Execution e Billing — ver
 * "Contratos de Mensagem"). Mudar um nome aqui exige mudar o mesmo nome no
 * {@code SagaMessages} do serviço participante.
 */
public final class SagaMessages {

    // Comandos enviados pelo orquestrador
    public static final String RESERVE_STOCK = "ComandoReservarEstoque";
    public static final String CONFIRM_RESERVATION = "ComandoConfirmarReserva";
    public static final String RELEASE_RESERVATION = "ComandoLiberarReserva";
    public static final String GENERATE_BUDGET = "ComandoGerarOrcamento";
    public static final String CHARGE_PAYMENT = "ComandoCobrarPagamento";

    // Eventos de resposta dos participantes
    public static final String STOCK_RESERVED = "ReservaConfirmada";
    public static final String RESERVATION_FAILED = "ReservaFalhou";
    public static final String RESERVATION_CONFIRMED = "ReservaBaixada";
    public static final String RESERVATION_RELEASED = "ReservaLiberada";
    public static final String BUDGET_GENERATED = "OrcamentoGerado";
    public static final String BUDGET_FAILED = "OrcamentoFalhou";
    public static final String CHARGE_CREATED = "CobrancaCriada";
    public static final String PAYMENT_CONFIRMED = "PagamentoConfirmado";
    public static final String PAYMENT_FAILED = "PagamentoFalhou";

    private SagaMessages() {}
}
