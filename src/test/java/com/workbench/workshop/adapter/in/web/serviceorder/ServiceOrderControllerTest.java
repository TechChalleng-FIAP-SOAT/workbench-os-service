package com.workbench.workshop.adapter.in.web.serviceorder;

import com.workbench.workshop.application.serviceorder.*;
import com.workbench.workshop.domain.serviceorder.InvalidStatusTransitionException;
import com.workbench.workshop.domain.serviceorder.OrderServiceStatusEnum;
import com.workbench.workshop.domain.serviceorder.ServiceOrder;
import com.workbench.workshop.infrastructure.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste do controller via MockMvc standalone (sem contexto Spring completo, sem
 * Docker). Inclui o GlobalExceptionHandler para validar os mapeamentos de erro
 * HTTP (422 para transição inválida, 404 para não encontrado).
 */
class ServiceOrderControllerTest {

    private final CreateOrderUseCase createOrderUseCase = mock(CreateOrderUseCase.class);
    private final UpdateOrderUseCase updateOrderUseCase = mock(UpdateOrderUseCase.class);
    private final DiagnoseOrderUseCase diagnoseOrderUseCase = mock(DiagnoseOrderUseCase.class);
    private final DecideOrderUseCase decideOrderUseCase = mock(DecideOrderUseCase.class);
    private final FindOrderByIdUseCase findOrderByIdUseCase = mock(FindOrderByIdUseCase.class);
    private final ListOrdersUseCase listOrdersUseCase = mock(ListOrdersUseCase.class);
    private final FindOrdersByDocumentUseCase findOrdersByDocumentUseCase = mock(FindOrdersByDocumentUseCase.class);

    private MockMvc mockMvc;

    private ServiceOrder sampleOrder() {
        return ServiceOrder.reconstitute(UUID.randomUUID(), "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.RECEBIDA, null,
                LocalDateTime.now(), LocalDateTime.now());
    }

    @BeforeEach
    void setUp() {
        var controller = new ServiceOrderController(createOrderUseCase, updateOrderUseCase,
                diagnoseOrderUseCase, decideOrderUseCase, findOrderByIdUseCase, listOrdersUseCase,
                findOrdersByDocumentUseCase);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createReturns201() throws Exception {
        when(createOrderUseCase.execute(any(), any(), any())).thenReturn(sampleOrder());

        mockMvc.perform(post("/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cpfCnpj\":\"52998224725\",\"placaVeiculo\":\"ABC1234\",\"serviceTypes\":[\"TROCA_OLEO\"]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceStatus").value("RECEBIDA"))
                .andExpect(jsonPath("$.placaVeiculo").value("ABC1234"));
    }

    @Test
    void listReturns200() throws Exception {
        when(listOrdersUseCase.execute()).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/order"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cpfCnpj").value("52998224725"));
    }

    @Test
    void findByIdReturns200() throws Exception {
        var order = sampleOrder();
        when(findOrderByIdUseCase.execute(any(UUID.class))).thenReturn(order);

        mockMvc.perform(get("/order/id/" + order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(order.getId().toString()));
    }

    @Test
    void findByDocumentReturns200() throws Exception {
        when(findOrdersByDocumentUseCase.execute("52998224725")).thenReturn(List.of(sampleOrder()));

        mockMvc.perform(get("/order/document/52998224725"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].placaVeiculo").value("ABC1234"));
    }

    @Test
    void updateReturns204() throws Exception {
        doNothing().when(updateOrderUseCase).execute(any(UUID.class), any());

        mockMvc.perform(patch("/order/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_DIAGNOSTICO\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidTransitionMappedTo422() throws Exception {
        doThrow(new InvalidStatusTransitionException("transição inválida"))
                .when(updateOrderUseCase).execute(any(UUID.class), any());

        mockMvc.perform(patch("/order/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void notFoundMappedTo404() throws Exception {
        doThrow(new NoSuchElementException("Ordem de servico nao encontrada"))
                .when(updateOrderUseCase).execute(any(UUID.class), any());

        mockMvc.perform(patch("/order/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_DIAGNOSTICO\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void diagnoseReturns200AndStartsSaga() throws Exception {
        var diagnosed = ServiceOrder.reconstitute(UUID.randomUUID(), "52998224725", "ABC1234",
                List.of("TROCA_OLEO"), "[TROCA_OLEO]", OrderServiceStatusEnum.EM_DIAGNOSTICO, null,
                LocalDateTime.now(), LocalDateTime.now());
        when(diagnoseOrderUseCase.execute(any(UUID.class), any(), any())).thenReturn(diagnosed);

        mockMvc.perform(post("/order/" + diagnosed.getId() + "/diagnosis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parts\":[{\"productId\":\"p1\",\"quantity\":2}],"
                                + "\"services\":[{\"serviceType\":\"TROCA_OLEO\",\"laborCost\":150.00,"
                                + "\"laborHours\":1.5,\"useDefault\":false}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceStatus").value("EM_DIAGNOSTICO"));
    }

    @Test
    void decideApprovedReturns204() throws Exception {
        doNothing().when(decideOrderUseCase).execute(any(UUID.class), any(), any());

        mockMvc.perform(post("/order/" + UUID.randomUUID() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void decideRejectedReturns204() throws Exception {
        doNothing().when(decideOrderUseCase).execute(any(UUID.class), any(), any());

        mockMvc.perform(post("/order/" + UUID.randomUUID() + "/decision")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"reason\":\"muito caro\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void illegalArgumentMappedTo400() throws Exception {
        doThrow(new IllegalArgumentException("status obrigatorio"))
                .when(updateOrderUseCase).execute(any(UUID.class), any());

        mockMvc.perform(patch("/order/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_DIAGNOSTICO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
