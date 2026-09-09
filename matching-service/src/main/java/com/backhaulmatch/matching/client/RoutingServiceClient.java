package com.backhaulmatch.matching.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Thin client for the local OSRM routing engine (docker-compose service "osrm").
 *
 * OSRM works purely in coordinates — it does NOT geocode place names — so the
 * caller resolves each location to {lat, lng} first (see DistanceCalculator,
 * which keeps a small city table solely as a geocoder fallback) and hands the
 * coordinates over here. Returns null on any routing failure so the caller can
 * degrade gracefully (great-circle fallback) instead of breaking matching.
 */
@Slf4j
@Component
public class RoutingServiceClient {

    private final RestClient restClient;

    public RoutingServiceClient(@Value("${routing.osrm.base-url:http://osrm:5000}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl.replaceAll("/+$", ""))
                .build();
    }

    /** Drivers note: OSRM's routes[0].distance is in METERS — divided here to km. */
    @SuppressWarnings("unchecked")
    public Double routeKm(double fromLat, double fromLon, double toLat, double toLon) {
        String coords = String.format("%f,%f;%f,%f", fromLon, fromLat, toLon, toLat);
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/route/v1/driving/{coords}?overview=false&alternatives=false", coords)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !"Ok".equals(body.get("code"))) return null;
            List<Map<String, Object>> routes = (List<Map<String, Object>>) body.get("routes");
            if (routes == null || routes.isEmpty()) return null;
            Object meters = routes.get(0).get("distance");
            return meters == null ? null : ((Number) meters).doubleValue() / 1000.0;
        } catch (RestClientException | ClassCastException e) {
            log.warn("OSRM route query failed for {}: {}", coords, e.getMessage());
            return null;
        }
    }

    /**
     * Multi-waypoint OSRM route with full geometry. Returns a two-element
     * array: {@code [totalDistanceKm, encodedPolyline]} — or {@code null} on
     * any failure so the caller can degrade gracefully.
     *
     * <p>Used by {@code DistanceCalculator.computeRouteProximity()} to measure
     * how close a shipment's pickup and delivery are to a vehicle's actual
     * road route.
     */
    @SuppressWarnings("unchecked")
    public Object[] routeWithGeometry(double[][] waypoints) {
        if (waypoints == null || waypoints.length < 2) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < waypoints.length; i++) {
            if (i > 0) sb.append(';');
            sb.append(String.format("%f,%f", waypoints[i][1], waypoints[i][0]));
        }
        String coords = sb.toString();
        try {
            Map<String, Object> body = restClient.get()
                    .uri("/route/v1/driving/{coords}?overview=full&geometries=polyline&alternatives=false", coords)
                    .retrieve()
                    .body(Map.class);
            if (body == null || !"Ok".equals(body.get("code"))) return null;
            List<Map<String, Object>> routes = (List<Map<String, Object>>) body.get("routes");
            if (routes == null || routes.isEmpty()) return null;
            Map<String, Object> route = routes.get(0);
            Object meters = route.get("distance");
            String geometry = (String) route.get("geometry");
            if (meters == null || geometry == null) return null;
            double distanceKm = ((Number) meters).doubleValue() / 1000.0;
            return new Object[]{distanceKm, geometry};
        } catch (RestClientException | ClassCastException e) {
            log.warn("OSRM route with geometry failed for {}: {}", coords, e.getMessage());
            return null;
        }
    }
}