package com.backhaulmatch.courier.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Map;

/** Calls payment-service directly (Eureka name) for the "cost savings" report figure. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private static final String URL = "http://PAYMENT-SERVICE/api/payment/reports/courier-summary";

    public BigDecimal getCostSavings(Long courierUserId) {
        try {
            String url = UriComponentsBuilder.fromHttpUrl(URL)
                    .queryParam("courierUserId", courierUserId)
                    .toUriString();
            Map<String, Object> result = restTemplate.getForObject(url, Map.class);
            return result == null ? BigDecimal.ZERO : new BigDecimal(result.get("costSavings").toString());
        } catch (Exception e) {
            log.warn("Could not fetch cost summary for user {}: {}", courierUserId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
