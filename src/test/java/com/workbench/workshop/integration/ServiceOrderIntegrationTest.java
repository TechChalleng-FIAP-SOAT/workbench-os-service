package com.workbench.workshop.integration;

import com.workbench.workshop.support.TestKeys;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Teste de integração end-to-end do OS Service: exercita controller, segurança
 * JWT, filtros, exception handler, Flyway e persistência real (Testcontainers).
 * Usa MockMvc com contexto completo. O seed V2 cria os tipos de serviço
 * TROCA_OLEO/ALINHAMENTO/REVISAO.
 */
@AutoConfigureMockMvc
class ServiceOrderIntegrationTest extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private String staffToken() {
        PrivateKey key = TestKeys.authPrivateKey();
        long now = System.currentTimeMillis();
        return TestKeys.authToken()
                .subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3600000))
                .signWith(key)
                .compact();
    }

    private String bearer() {
        return "Bearer " + staffToken();
    }

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/order")).andExpect(status().isUnauthorized());
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void fullLifecycle_createListFindUpdate() throws Exception {
        String body = """
                {"cpfCnpj":"52998224725","placaVeiculo":"abc1234","serviceTypes":["TROCA_OLEO"]}
                """;
        MvcResult created = mockMvc.perform(post("/order")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serviceStatus").value("RECEBIDA"))
                .andExpect(jsonPath("$.placaVeiculo").value("ABC1234"))
                .andReturn();

        String id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/order/id/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/order").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/order/document/52998224725").header("Authorization", bearer()))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/order/" + id)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"EM_DIAGNOSTICO\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidTransitionReturns422() throws Exception {
        String body = """
                {"cpfCnpj":"11144477735","placaVeiculo":"xyz9876","serviceTypes":["ALINHAMENTO"]}
                """;
        MvcResult created = mockMvc.perform(post("/order")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(patch("/order/" + id)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ENTREGUE\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void createWithUnknownServiceTypeReturns422() throws Exception {
        String body = """
                {"cpfCnpj":"52998224725","placaVeiculo":"aaa1111","serviceTypes":["NAO_EXISTE"]}
                """;
        mockMvc.perform(post("/order")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    private String json(String method, String url, String body, int expectedStatus) throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PUT" -> put(url);
            case "PATCH" -> patch(url);
            default -> get(url);
        };
        MvcResult result = mockMvc.perform(request.header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body == null ? "" : body))
                .andExpect(status().is(expectedStatus))
                .andReturn();
        return result.getResponse().getContentAsString();
    }

    @Test
    void serviceTypeCrud() throws Exception {
        String created = json("POST", "/service-types",
                "{\"name\":\"troca de correia\",\"description\":\"Correia dentada\",\"laborCost\":180}", 201);
        String id = mapper.readTree(created).get("id").asText();
        org.junit.jupiter.api.Assertions.assertEquals("TROCA_DE_CORREIA", mapper.readTree(created).get("name").asText());

        // nome duplicado (mesmo depois de normalizado) → 409
        json("POST", "/service-types", "{\"name\":\"TROCA DE CORREIA\"}", 409);

        json("PUT", "/service-types/" + id, "{\"description\":\"Correia + tensor\",\"laborCost\":220,\"laborHours\":2.5}", 200);
        mockMvc.perform(get("/service-types/" + id).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Correia + tensor"))
                .andExpect(jsonPath("$.laborCost").value(220));

        mockMvc.perform(delete("/service-types/" + id).header("Authorization", bearer()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/service-types/" + id).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void executingAllServicesFinalizesTheOrderAndFeedsMonitoring() throws Exception {
        String created = json("POST", "/order",
                "{\"cpfCnpj\":\"52998224725\",\"placaVeiculo\":\"exe1234\",\"serviceTypes\":[\"TROCA_OLEO\"]}", 201);
        String osId = mapper.readTree(created).get("id").asText();

        // modo manual (sem Saga): leva a OS até EM_EXECUCAO
        for (String st : List.of("EM_DIAGNOSTICO", "AGUARDANDO_APROVACAO", "APROVADO", "EM_EXECUCAO")) {
            json("PATCH", "/order/" + osId, "{\"status\":\"" + st + "\"}", 204);
        }
        String services = json("GET", "/order/" + osId + "/services", null, 200);
        String serviceId = mapper.readTree(services).get(0).get("id").asText();

        json("PATCH", "/order/" + osId + "/services/" + serviceId + "/status", "{\"status\":\"DONE\"}", 422);
        json("PATCH", "/order/" + osId + "/services/" + serviceId + "/status", "{\"status\":\"DOING\"}", 200);
        json("PATCH", "/order/" + osId + "/services/" + serviceId + "/status", "{\"status\":\"DONE\"}", 200);

        mockMvc.perform(get("/order/id/" + osId).header("Authorization", bearer()))
                .andExpect(jsonPath("$.serviceStatus").value("FINALIZADA"));
        mockMvc.perform(get("/order/" + osId + "/services").header("Authorization", bearer()))
                .andExpect(jsonPath("$[0].status").value("DONE"))
                .andExpect(jsonPath("$[0].history.length()").value(3));
    }

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        // Mesmo sem token (401), toda resposta leva os cabeçalhos de proteção.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/order"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Frame-Options", "DENY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cross-Origin-Resource-Policy", "same-origin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cross-Origin-Opener-Policy", "same-origin"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cross-Origin-Embedder-Policy", "require-corp"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentTypeCompatibleWith("application/json"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value(401));
    }
}
