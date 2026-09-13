package com.backhaulmatch.courier.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Map;

/** Calls payment-service directly (Eureka name) for the "cost savings" report figure + price estimate. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceClient {

    private final RestTemplate restTemplate;
    private static final String URL = "http://PAYMENT-SERVICE/api/payment/reports/courier-summary";
    private static final String ESTIMATE_URL = "http://PAYMENT-SERVICE/api/payment/pricing/estimate/internal";

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

    /**
     * "Before you commit" price estimate for a shipment about to be created.
     * Returns null if payment-service is unreachable (cost is a nice-to-have,
     * not a blocker for creating the shipment).
     */
    @SuppressWarnings("unchecked")
    public BigDecimal estimatePrice(Double distanceKm, BigDecimal weightKg, String vehicleType, String priority) {
        try {
            java.util.Map<String, Object> body = new java.util.HashMap<>();
            body.put("distanceKm", distanceKm);
            body.put("weightKg", weightKg);
            body.put("vehicleType", vehicleType);
            body.put("priority", priority);
            body.put("backhaul", false);
            java.util.Map<String, Object> result = restTemplate.postForObject(ESTIMATE_URL, body, Map.class);
            if (result == null) return null;
            java.util.Map<String, Object> breakdown = (java.util.Map<String, Object>) result.get("breakdown");
            return breakdown == null ? null : new BigDecimal(breakdown.get("estimatedAmount").toString());
        } catch (Exception e) {
            log.warn("Could not estimate price for shipment: {}", e.getMessage());
            return null;
        }
    }
}
