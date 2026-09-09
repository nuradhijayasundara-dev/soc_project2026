package com.backhaulmatch.gps.service;

import com.backhaulmatch.gps.client.CourierServiceClient;
import com.backhaulmatch.gps.client.FleetServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Resolves which trucks a user is allowed to see the location of:
 *
 *   FLEET_MANAGER    -> their own fleet company's trucks
 *   COURIER_OPERATOR -> trucks hauling their company's shipments
 *   DRIVER           -> the truck(s) on their assigned trips
 *   ADMIN (default)  -> everything (null = unrestricted)
 *
 * The X-User-Id / X-User-Role headers are injected by the Gateway's JWT filter,
 * so they can be trusted here.
 */
@Service
@RequiredArgsConstructor
public class GpsAccessService {

    private final FleetServiceClient fleetClient;
    private final CourierServiceClient courierClient;

    /** null means unrestricted (ADMIN); an empty list means the user can see nothing. */
    public List<Long> allowedTruckIds(Long userId, String role) {
        if (userId == null) return List.of();
        return switch (role == null ? "" : role) {
            case "FLEET_MANAGER" -> fleetClient.getCompanyTruckIds(userId);
            case "COURIER_OPERATOR" -> resolveCourierTruckIds(userId);
            case "DRIVER" -> fleetClient.getDriverTruckIds(userId);
            default -> null;
        };
    }

    public boolean canAccessTruck(Long userId, String role, Long truckId) {
        List<Long> allowed = allowedTruckIds(userId, role);
        return allowed == null || allowed.contains(truckId);
    }

    private List<Long> resolveCourierTruckIds(Long userId) {
        List<Long> shipmentIds = courierClient.getCompanyShipmentIds(userId);
        if (shipmentIds.isEmpty()) return List.of();
        return fleetClient.getTruckIdsForShipments(shipmentIds);
    }
}
