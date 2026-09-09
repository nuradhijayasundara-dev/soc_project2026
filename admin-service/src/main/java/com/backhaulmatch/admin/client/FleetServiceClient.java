package com.backhaulmatch.admin.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Reads fleet-service's internal admin endpoints (Eureka name) for the
 * dashboard count and company-approval workflow.
 */
@Component
@RequiredArgsConstructor
public class FleetServiceClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://FLEET-SERVICE/internal/admin/companies";

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listCompanies() {
        return restTemplate.getForObject(BASE_URL, List.class);
    }

    public long countCompanies() {
        Map<String, Object> body = restTemplate.getForObject(BASE_URL + "/count", Map.class);
        return body == null ? 0 : ((Number) body.get("count")).longValue();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setStatus(Long id, String action) {
        return restTemplate.exchange(BASE_URL + "/" + id + "/" + action,
                HttpMethod.PATCH, null, Map.class).getBody();
    }
}
