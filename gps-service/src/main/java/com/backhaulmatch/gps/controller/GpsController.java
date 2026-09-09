package com.backhaulmatch.gps.controller;

import com.backhaulmatch.gps.client.FleetServiceClient;
import com.backhaulmatch.gps.dto.GpsDtos.LocationUpdateRequest;
import com.backhaulmatch.gps.entity.GpsTrackingHistory;
import com.backhaulmatch.gps.entity.LiveGpsLocation;
import com.backhaulmatch.gps.service.GpsAccessService;
import com.backhaulmatch.gps.service.GpsTrackingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * All requests pass through the Gateway (/api/gps/**) and are already JWT-validated
 * there — the X-User-Id / X-User-Role headers are injected by the Gateway's JWT
 * filter. Every read is scoped by role via {@link GpsAccessService}:
 * a fleet manager only sees their own company's trucks, a courier only sees the
 * trucks hauling their company's shipments, and a driver only sees their own truck.
 */
@RestController
@RequestMapping("/api/gps")
@RequiredArgsConstructor
public class GpsController {

    private final GpsTrackingService gpsTrackingService;
    private final GpsAccessService gpsAccessService;
    private final FleetServiceClient fleetServiceClient;

    // Driver App -> Backend: called every few seconds while a trip is active.
    // Only the truck(s) on the driver's own assigned trips may be reported on.
    @PostMapping("/location")
    public ResponseEntity<LiveGpsLocation> reportLocation(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody LocationUpdateRequest request) {
        if (!"DRIVER".equals(role) || !gpsAccessService.canAccessTruck(userId, role, request.truckId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(gpsTrackingService.recordLocation(request));
    }

    // Fleet/Courier portal map: this user's trucks' current positions, optionally
    // narrowed further via ?truckIds=1,2,3.
    @GetMapping("/live")
    public ResponseEntity<List<LiveGpsLocation>> getLive(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam(required = false) String truckIds) {
        List<Long> allowed = gpsAccessService.allowedTruckIds(userId, role);
        List<Long> requested = (truckIds == null || truckIds.isBlank())
                ? null
                : Arrays.stream(truckIds.split(",")).map(Long::parseLong).toList();
        return ResponseEntity.ok(gpsTrackingService.getLiveForTrucks(restrict(allowed, requested)));
    }

    @GetMapping("/live/{truckId}")
    public ResponseEntity<LiveGpsLocation> getLiveForTruck(
            @PathVariable Long truckId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!gpsAccessService.canAccessTruck(userId, role, truckId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return gpsTrackingService.getLiveForTrucks(List.of(truckId)).stream().findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Tracking history — for playback on the courier tracking page or trip replay
    @GetMapping("/history/truck/{truckId}")
    public ResponseEntity<List<GpsTrackingHistory>> getHistoryForTruck(
            @PathVariable Long truckId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!gpsAccessService.canAccessTruck(userId, role, truckId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(gpsTrackingService.getHistoryForTruck(truckId));
    }

    @GetMapping("/history/trip/{tripId}")
    public ResponseEntity<List<GpsTrackingHistory>> getHistoryForTrip(
            @PathVariable Long tripId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        Long truckId = fleetServiceClient.getTripTruckId(tripId);
        if (truckId == null) {
            return ResponseEntity.notFound().build();
        }
        if (!gpsAccessService.canAccessTruck(userId, role, truckId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(gpsTrackingService.getHistoryForTrip(tripId));
    }

    /** Applies the caller's allowed-truck set on top of any requested filter (null = unrestricted). */
    private List<Long> restrict(List<Long> allowed, List<Long> requested) {
        if (allowed == null) return requested;
        if (requested == null) return allowed;
        return requested.stream().filter(allowed::contains).toList();
    }
}
