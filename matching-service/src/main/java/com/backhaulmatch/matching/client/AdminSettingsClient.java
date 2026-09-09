package com.backhaulmatch.matching.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Reads System Settings from admin-service (Eureka name, not through the
 * Gateway). Best-effort: if admin-service is down or the call fails, matching
 * silently falls back to its compiled-in defaults so the engine still runs.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSettingsClient {

    private final RestTemplate restTemplate;
    private static final String SETTINGS_URL = "http://ADMIN-SERVICE/api/admin/settings/internal";

    public Map<String, String> getSettings() {
        try {
            List<Map<String, Object>> settings = restTemplate.exchange(
                    SETTINGS_URL, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {}).getBody();
            if (settings == null) return Collections.emptyMap();

            return settings.stream().filter(s -> s.get("key") != null && s.get("value") != null)
                    .collect(Collectors.toMap(s -> String.valueOf(s.get("key")),
                            s -> String.valueOf(s.get("value")), (a, b) -> b));
        } catch (Exception e) {
            log.warn("Could not fetch system settings from admin-service: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
