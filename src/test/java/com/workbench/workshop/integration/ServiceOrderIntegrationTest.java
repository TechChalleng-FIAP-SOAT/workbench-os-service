package com.workbench.workshop.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
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

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private String staffToken() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        return Jwts.builder()
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
}
