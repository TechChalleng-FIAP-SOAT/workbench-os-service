package com.workbench.workshop.infrastructure.config;

import com.workbench.workshop.application.service.UnknownServiceTypeException;
import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort.CustomerServiceUnavailableException;
import com.workbench.workshop.application.serviceorder.port.out.CustomerValidationPort.CustomerValidationException;
import lombok.extern.slf4j.Slf4j;
import com.workbench.workshop.domain.serviceorder.InvalidStatusTransitionException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        String friendly = message != null && message.contains("duplicate key")
                ? "Registro duplicado: já existe um recurso com esse valor único."
                : "Violação de integridade dos dados.";
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 409,
                "error", friendly
        ));
    }

    /** Outra requisição alterou a mesma OS ao mesmo tempo (ex.: aprovar e recusar juntos). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> handleConcurrentUpdate(OptimisticLockingFailureException ex) {
        log.warn("Atualização concorrente descartada: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 409,
                "error", "A ordem de serviço foi alterada por outra requisição ao mesmo tempo. Consulte a OS e tente de novo."
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 400,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 404,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(CustomerValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerValidation(
            CustomerValidationException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 404,
                "error", ex.getMessage()
        ));
    }

    /** Dependência fora do ar: o detalhe técnico vai para o log, o cliente recebe mensagem genérica. */
    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCustomerServiceUnavailable(CustomerServiceUnavailableException ex) {
        log.error("Customer Service indisponível", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 503,
                "error", "Serviço de clientes indisponível no momento. Tente novamente em instantes."
        ));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidStatusTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 422,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 403,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 409,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 400,
                "errors", errors
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", 400,
                "errors", errors
        ));
    }

    /** Tipo de serviço inexistente no corpo do pedido: regra de negócio (422), não rota inexistente (404). */
    @ExceptionHandler(UnknownServiceTypeException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownServiceType(UnknownServiceTypeException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    /** UUID/número inválido na URL (ex.: {@code /order/abc}) é erro do cliente, não 500/422. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' inválido: '" + ex.getValue() + "'.");
    }

    /** JSON mal formado ou valor fora do enum — sem repassar a mensagem do Jackson. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        return error(HttpStatus.BAD_REQUEST,
                "Corpo da requisição inválido: confira o JSON e os tipos/valores dos campos.");
    }

    /**
     * Catch-all: não expõe detalhes internos (OWASP A05) — loga no servidor e responde
     * genérico. Exceções do próprio Spring MVC (405, 415, 404…) mantêm o status delas.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse framework) {
            HttpStatus status = HttpStatus.valueOf(framework.getStatusCode().value());
            return error(status, frameworkMessage(status));
        }
        log.error("Erro inesperado", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno. Tente novamente mais tarde.");
    }

    private static String frameworkMessage(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso não encontrado.";
            case METHOD_NOT_ALLOWED -> "Método HTTP não suportado nesta rota.";
            case UNSUPPORTED_MEDIA_TYPE -> "Content-Type não suportado; use application/json.";
            case NOT_ACCEPTABLE -> "Formato de resposta não suportado.";
            default -> "Requisição inválida.";
        };
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", message
        ));
    }
}
