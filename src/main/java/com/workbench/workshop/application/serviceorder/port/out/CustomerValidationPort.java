package com.workbench.workshop.application.serviceorder.port.out;

/**
 * Porta de saída para validar cliente e veículo no Customer Service (Fase 4, Etapa 6).
 *
 * <p>O OS Service não acessa o banco do Customer: valida por REST na criação da OS.
 * A implementação real chama o Customer Service; o fallback no-op permite rodar/testar
 * o OS Service sem a integração ligada (dev/testes).
 */
public interface CustomerValidationPort {

    /**
     * Garante que existe cliente ativo com o documento informado.
     * @throws CustomerValidationException se o cliente não existe (ou a validação falha)
     */
    void validateClientExists(String cpfCnpj);

    /**
     * Garante que existe veículo com a placa informada.
     * @throws CustomerValidationException se o veículo não existe (ou a validação falha)
     */
    void validateVehicleExists(String placa);

    /** Falha de validação de cliente/veículo (mapeada para 404/erro de negócio). */
    class CustomerValidationException extends RuntimeException {
        public CustomerValidationException(String message) {
            super(message);
        }
    }
}
