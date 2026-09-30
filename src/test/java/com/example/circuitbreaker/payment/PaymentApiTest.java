package com.example.circuitbreaker.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Boots the full application and drives it over HTTP. Doubles as the "does the
 * context load" check. The breaker's own rules are covered in {@code CircuitBreakerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentApiTest {

    private static final String PAYMENT_JSON = """
            {"reference": "order-1", "amount": 25.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockBankClient mockBankClient;

    @AfterEach
    void restoreBankHealth() {
        mockBankClient.setHealthy(true);
    }

    @Test
    void paymentIsApprovedWhenTheBankIsHealthy() throws Exception {
        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(PAYMENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("order-1"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.circuitState").value("CLOSED"));
    }

    @Test
    void paymentIsDeclinedWhenTheBankIsUnhealthy() throws Exception {
        mockMvc.perform(put("/mock-bank").param("healthy", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.healthy").value(false));

        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content(PAYMENT_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DECLINED"));
    }
}
