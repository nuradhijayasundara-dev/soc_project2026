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
 * Direct service-to-service calls (not via the Gateway) to fleet-service,
 * resolved by its Eureka name "FLEET-SERVICE" through the @LoadBalanced
 * RestTemplate. Used by GpsAccessService to decide which trucks a user may
 * see the location of — fleet managers get their own company's trucks,
 * drivers get the trucks on their assigned trips.
 */
@Component
@RequiredArgsConstructor
public class FleetServiceClient {

    private final RestTemplate restTemplate;

    private static final String FLEET_SERVICE_URL = "http://FLEET-SERVICE/api/fleet";

    /** Every truck id belonging to the fleet company a user belongs to. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Long> getCompanyTruckIds(Long userId) {
        List<Map<String, Object>> trucks = getWithUserHeader(FLEET_SERVICE_URL + "/trucks", userId, List.class);
        if (trucks == null) return Collections.emptyList();
        return trucks.stream()
                .map(t -> ((Number) t.get("id")).longValue())
                .distinct()
                .toList();
    }

    /** Truck ids on the trips assigned to a driver (current + past). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Long> getDriverTruckIds(Long userId) {
        List<Map<String, Object>> trips = getWithUserHeader(FLEET_SERVICE_URL + "/trips/my", userId, List.class);
        if (trips == null) return Collections.emptyList();
        return trips.stream()
                .map(t -> ((Number) t.get("truckId")).longValue())
                .distinct()
                .toList();
    }

    /** Truck ids with a Trip against any of the given shipment ids. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<Long> getTruckIdsForShipments(List<Long> shipmentIds) {
        String query = String.join(",", shipmentIds.stream().map(String::valueOf).toList());
        List<Map<String, Object>> trips = restTemplate.getForObject(
                FLEET_SERVICE_URL + "/trips/internal/by-shipments?shipmentIds=" + query, List.class);
        if (trips == null) return Collections.emptyList();
        return trips.stream()
                .map(t -> ((Number) t.get("truckId")).longValue())
                .distinct()
                .toList();
    }

    /** The truck a trip runs on, or null if the trip doesn't exist. */
    public Long getTripTruckId(Long tripId) {
        try {
            Map<String, Object> trip = restTemplate.getForObject(FLEET_SERVICE_URL + "/trips/" + tripId, Map.class);
            return trip == null ? null : ((Number) trip.get("truckId")).longValue();
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        }
    }

    private <T> T getWithUserHeader(String url, Long userId, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Id", String.valueOf(userId));
        ResponseEntity<T> resp = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), type);
        return resp.getBody();
    }
}
