package com.backhaulmatch.fleet.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Example of direct service-to-service communication (not via the Gateway):
 * fleet-service needs to confirm a shipment exists (and read its weight/route)
 * before creating a Trip against it, and to move a shipment to IN_TRANSIT when
 * a driver starts the trip. It calls courier-service by its Eureka name —
 * "COURIER-SERVICE" — resolved by the @LoadBalanced RestTemplate.
 *
 * Internal service calls like this skip the Gateway's JWT filter entirely,
 * since they happen inside the trusted backend network.
 */
@Component
@RequiredArgsConstructor
public class CourierServiceClient {

    private final RestTemplate restTemplate;

    private static final String COURIER_SERVICE_URL = "http://COURIER-SERVICE/api/courier/shipments/";

    /** Returns the shipment as a raw map, or throws 404 if courier-service doesn't have it. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getShipment(Long shipmentId) {
        try {
            return restTemplate.getForObject(COURIER_SERVICE_URL + shipmentId, Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Shipment " + shipmentId + " does not exist in courier-service");
        }
    }

    /**
     * Advances the shipment to the given status (e.g. IN_TRANSIT when the trip
     * starts, DELIVERED when it finishes). The endpoint is JWT-free on the
     * courier-service side — this is a trusted service-to-service call.
     */
    public void updateShipmentStatus(Long shipmentId, String status, String location) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request =
                new HttpEntity<>(Map.of("status", status, "location", location == null ? "" : location), headers);
        restTemplate.exchange(COURIER_SERVICE_URL + shipmentId + "/status", HttpMethod.PATCH, request, Map.class);
    }
}
