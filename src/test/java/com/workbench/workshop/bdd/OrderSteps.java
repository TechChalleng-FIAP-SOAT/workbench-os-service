package com.workbench.workshop.bdd;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Step definitions do fluxo da OS. Dirige o serviço pela API REST (MockMvc), com
 * segurança JWT real. O avanço de status usa o PATCH /order/{id}, cobrindo a
 * máquina de estados do domínio ponta a ponta (incluindo a transição inválida).
 */
public class OrderSteps {

    private static final String SECRET = "akmsdkmasfnjsgknakmsdkmasdkmsadfkmdsakmdkmsadsa";

    @Autowired private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper();

    private String token;
    private String orderId;
    private int lastStatusCode;

    private String bearer() {
        return "Bearer " + token;
    }

    @Dado("que estou autenticado como atendente")
    public void autenticadoComoAtendente() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        long now = System.currentTimeMillis();
        token = Jwts.builder()
                .subject("admin@system.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(new Date(now))
                .expiration(new Date(now + 3_600_000))
                .signWith(key)
                .compact();
    }

    @Quando("eu crio uma OS para o cliente {string} e veículo {string} com o serviço {string}")
    public void crioUmaOs(String documento, String placa, String servico) throws Exception {
        String body = """
                {"cpfCnpj":"%s","placaVeiculo":"%s","serviceTypes":["%s"]}
                """.formatted(documento, placa, servico);
        MvcResult res = mockMvc.perform(post("/order")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        lastStatusCode = res.getResponse().getStatus();
        if (lastStatusCode == 201) {
            orderId = mapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        }
    }

    @Então("a OS é criada com status {string}")
    public void aOsECriadaComStatus(String status) throws Exception {
        assertEquals(201, lastStatusCode, "esperava 201 Created");
        verificaStatusAtual(status);
    }

    @Quando("a OS avança para {string}")
    public void aOsAvancaPara(String status) throws Exception {
        MvcResult res = mockMvc.perform(patch("/order/" + orderId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andReturn();
        assertEquals(204, res.getResponse().getStatus(),
                "transição para " + status + " deveria retornar 204");
    }

    @Quando("eu tento avançar a OS diretamente para {string}")
    public void euTentoAvancarDiretamente(String status) throws Exception {
        MvcResult res = mockMvc.perform(patch("/order/" + orderId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + status + "\"}"))
                .andReturn();
        lastStatusCode = res.getResponse().getStatus();
    }

    @Então("a OS tem status {string}")
    public void aOsTemStatus(String status) throws Exception {
        verificaStatusAtual(status);
    }

    @Então("a transição é rejeitada com erro de regra de negócio")
    public void aTransicaoERejeitada() {
        assertTrue(lastStatusCode == 422 || lastStatusCode == 400,
                "esperava 422/400 para transição inválida, veio " + lastStatusCode);
    }

    private void verificaStatusAtual(String status) throws Exception {
        MvcResult res = mockMvc.perform(get("/order/id/" + orderId).header("Authorization", bearer()))
                .andReturn();
        assertEquals(200, res.getResponse().getStatus());
        String atual = mapper.readTree(res.getResponse().getContentAsString()).get("serviceStatus").asText();
        assertEquals(status, atual);
    }
}
