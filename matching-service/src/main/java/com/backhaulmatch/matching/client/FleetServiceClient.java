package com.backhaulmatch.matching.client;

import com.backhaulmatch.matching.dto.MatchingDtos.AvailabilityCandidate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Searches fleet-service's cross-company backhaul availability directly
 * (Eureka name "FLEET-SERVICE") — this is the core input to the capacity
 * matching logic below.
 */
@Component
@RequiredArgsConstructor
public class FleetServiceClient {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "http://FLEET-SERVICE/api/fleet/availability/search";

    public List<AvailabilityCandidate> searchAvailability(String from, String to, BigDecimal minCapacityTon) {
        String url = UriComponentsBuilder.fromHttpUrl(BASE_URL)
                .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                .queryParam("minCapacityTon", minCapacityTon)
                .toUriString();

        AvailabilityCandidate[] results = restTemplate.getForObject(url, AvailabilityCandidate[].class);
        return results == null ? List.of() : Arrays.asList(results);
    }
}
