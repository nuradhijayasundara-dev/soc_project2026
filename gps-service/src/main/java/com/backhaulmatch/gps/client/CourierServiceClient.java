package com.backhaulmatch.gps.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Direct service-to-service call (not via the Gateway) to courier-service,
 * resolved by its Eureka name "COURIER-SERVICE". Used by GpsAccessService to
 * scope a courier's GPS access to the trucks hauling their company's shipments.
 */
@Component
@RequiredArgsConstructor
public class CourierServiceClient {

    private final RestTemplate restTemplate;

    private static final String COURIER_SERVICE_URL = "http://COURIER-SERVICE/api/courier/shipments";

    /** Every shipment id belonging to the courier company a user belongs to. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Long> getCompanyShipmentIds(Long userId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    COURIER_SERVICE_URL, HttpMethod.GET, new HttpEntity<>(headers), List.class);
            List<Map<String, Object>> shipments = resp.getBody();
            if (shipments == null) return Collections.emptyList();
            return shipments.stream()
                    .map(s -> ((Number) s.get("id")).longValue())
                    .distinct()
                    .toList();
        } catch (HttpClientErrorException e) {
            // e.g. the courier has no company registered yet — nothing to scope against
            return Collections.emptyList();
        }
    }
}
