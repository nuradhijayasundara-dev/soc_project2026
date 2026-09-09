package com.backhaulmatch.admin.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls auth-service's internal admin endpoints (Eureka name, not through the
 * Gateway) for user management. Auth-service keeps these under "/internal/admin",
 * outside the Gateway's routes, so they can never be hit publicly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://AUTH-SERVICE/internal/admin/users";

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listUsers() {
        return restTemplate.getForObject(BASE_URL, List.class);
    }

    public long countUsers() {
        Map<String, Object> body = restTemplate.getForObject(BASE_URL + "/count", Map.class);
        return body == null ? 0 : ((Number) body.get("count")).longValue();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createUser(String username, String email, String password, String role) {
        return restTemplate.postForObject(BASE_URL,
                Map.of("username", username, "email", email, "password", password, "role", role), Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> updateUser(Long id, String username, String email, String password, String role) {
        Map<String, Object> body = new HashMap<>();
        if (username != null && !username.isBlank()) body.put("username", username);
        if (email != null && !email.isBlank()) body.put("email", email);
        if (password != null && !password.isBlank()) body.put("password", password);
        if (role != null && !role.isBlank()) body.put("role", role);
        return restTemplate.exchange(BASE_URL + "/" + id, HttpMethod.PUT,
                new HttpEntity<>(body), Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setUserEnabled(Long id, boolean enabled) {
        return restTemplate.exchange(BASE_URL + "/" + id + "/enabled?enabled=" + enabled,
                HttpMethod.PATCH, null, Map.class).getBody();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setApprovalStatus(Long id, String status) {
        return restTemplate.exchange(BASE_URL + "/" + id + "/approval?status=" + status,
                HttpMethod.PATCH, null, Map.class).getBody();
    }

    public void deleteUser(Long id) {
        restTemplate.exchange(BASE_URL + "/" + id, HttpMethod.DELETE, null, Void.class);
    }
}
