package com.example.circuitbreaker.payment;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only control surface for the {@link MockBankClient}.
 *
 * <pre>
 * PUT /mock-bank?healthy=false   make the bank start failing
 * PUT /mock-bank?healthy=true    make the bank recover
 * GET /mock-bank                 show current health
 * </pre>
 */
@RestController
@RequestMapping("/mock-bank")
public class MockBankController {

    private final MockBankClient mockBankClient;

    public MockBankController(MockBankClient mockBankClient) {
        this.mockBankClient = mockBankClient;
    }

    @GetMapping
    public Map<String, Boolean> status() {
        return Map.of("healthy", mockBankClient.isHealthy());
    }

    @PutMapping
    public Map<String, Boolean> setHealthy(@RequestParam boolean healthy) {
        mockBankClient.setHealthy(healthy);
        return status();
    }
}
