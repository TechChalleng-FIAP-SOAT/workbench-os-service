package com.workbench.workshop.infrastructure.notification;

import com.workbench.workshop.domain.serviceorder.OrderStatusLabel;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;

/**
 * Assunto e corpo dos e-mails ao cliente. Compartilhado pelos canais (SNS e SES) para que o
 * texto seja o mesmo, qualquer que seja o transporte.
 */
final class CustomerNotificationMessages {

    /** Assunto e corpo de um e-mail. */
    record Email(String subject, String body) {}

    private CustomerNotificationMessages() {}

    static Email statusUpdate(ServiceOrder order) {
        String status = OrderStatusLabel.of(order.getStatus());
        return new Email("OS Management - Atualizacao de Status: " + status,
                buildMessage(order.getCpfCnpj(), order.getLicensePlate(), status));
    }

    static Email paymentLink(ServiceOrder order, String checkoutUrl) {
        return new Email("OS Management - Link de Pagamento da sua Ordem de Servico",
                buildPaymentMessage(order.getCpfCnpj(), order.getLicensePlate(), checkoutUrl));
    }

    /**
     * Pagamento recusado. Não repete o motivo técnico do gateway ao cliente: ele só precisa saber
     * que não foi aprovado e o que fazer.
     */
    static Email paymentFailed(ServiceOrder order) {
        return new Email("OS Management - Pagamento nao aprovado da sua Ordem de Servico",
                buildPaymentFailedMessage(order.getCpfCnpj(), order.getLicensePlate()));
    }

    private static String buildPaymentFailedMessage(String clientDocument, String vehicleInfo) {
        return String.format("""
                ============================================
                OS Management - Pagamento nao aprovado
                ============================================
                Ola,
                O pagamento da sua ordem de servico nao foi aprovado.
                Sua ordem continua aguardando o pagamento. Para tentar novamente,
                solicite um novo link de pagamento a oficina.
                Cliente (documento): %s
                Veiculo: %s
                --------------------------------------------
                Oficina Mecanica - OS Management
                Este e um email automatico.
                ============================================
                """, clientDocument, vehicleInfo);
    }

    private static String buildPaymentMessage(String clientDocument, String vehicleInfo, String checkoutUrl) {
        return String.format("""
                ============================================
                OS Management - Pagamento da Ordem de Servico
                ============================================
                Ola,
                O orcamento da sua ordem de servico foi aprovado e o servico finalizado.
                Para concluir, realize o pagamento pelo link abaixo:
                %s
                Cliente (documento): %s
                Veiculo: %s
                --------------------------------------------
                Oficina Mecanica - OS Management
                Este e um email automatico.
                ============================================
                """, checkoutUrl, clientDocument, vehicleInfo);
    }

    private static String buildMessage(String clientDocument, String vehicleInfo, String status) {
        return String.format("""
                ============================================
                OS Management - Notificacao de Status
                ============================================
                Ola,
                %s
                Status atual: %s
                Cliente (documento): %s
                Veiculo: %s
                --------------------------------------------
                Oficina Mecanica - OS Management
                Este e um email automatico.
                ============================================
                """, getStatusMessage(status), status, clientDocument, vehicleInfo);
    }

    private static String getStatusMessage(String status) {
        return switch (status) {
            case "Recebida" -> "Sua ordem de servico foi recebida e esta em nossa fila de atendimento.";
            case "Em Diagnostico" -> "Seu veiculo esta sendo avaliado pelo nosso mecanico.";
            case "Aguardando Aprovacao" -> "O diagnostico foi concluido. Por favor, avalie o orcamento para aprovar ou recusar.";
            case "Aprovado" -> "O orcamento foi aprovado! Nosso mecanico iniciara os servicos em breve.";
            case "Em Execucao" -> "Os servicos estao sendo realizados no seu veiculo.";
            case "Finalizada" -> "Todos os servicos foram concluidos! Seu veiculo esta pronto para retirada.";
            case "Pagamento Pendente" -> "Aguardando a confirmacao do pagamento da sua ordem de servico.";
            case "Pago" -> "Pagamento confirmado! Obrigado.";
            case "Entregue" -> "Seu veiculo foi entregue. Obrigado pela confianca!";
            case "Recusada" -> "O orcamento foi recusado. Entre em contato conosco caso deseje reavaliar.";
            default -> "O status da sua ordem de servico foi atualizado para: " + status;
        };
    }
}
