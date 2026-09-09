package com.backhaulmatch.fleet.controller;

import com.backhaulmatch.fleet.dto.TripDtos.AssignDriverRequest;
import com.backhaulmatch.fleet.dto.TripDtos.CreateTripFromBookingRequest;
import com.backhaulmatch.fleet.dto.TripDtos.CreateTripRequest;
import com.backhaulmatch.fleet.entity.Trip;
import com.backhaulmatch.fleet.repository.TripRepository;
import com.backhaulmatch.fleet.repository.TruckRepository;
import com.backhaulmatch.fleet.service.FleetCompanyService;
import com.backhaulmatch.fleet.service.DriverService;
import com.backhaulmatch.fleet.service.TripService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/fleet/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;
    private final FleetCompanyService companyService;
    private final DriverService driverService;
    private final TruckRepository truckRepository;
    private final TripRepository tripRepository;

    @GetMapping
    public ResponseEntity<List<Trip>> list(@RequestHeader("X-User-Id") Long userId) {
        Long companyId = companyService.resolveCompanyId(userId);
        List<Long> truckIds = truckRepository.findByFleetCompanyId(companyId).stream()
                .map(t -> t.getId()).collect(Collectors.toList());
        return ResponseEntity.ok(tripService.listForTrucks(truckIds));
    }

    // Driver App: trips assigned to the logged-in driver — enriched with truck no
    // + shipment pickup/destination/route so the app can render the run. Feeds the
    // "select truck/trip" screen AND the active-trip screen.
    @GetMapping("/my")
    public ResponseEntity<List<com.backhaulmatch.fleet.dto.TripDtos.DriverTripResponse>> myTrips(@RequestHeader("X-User-Id") Long userId) {
        Long driverId = driverService.getByUserId(userId).getId();
        return ResponseEntity.ok(tripService.listForDriver(driverId));
    }

    // Internal (service-to-service, Eureka name — not via the Gateway): used by
    // gps-service to work out which trucks a courier is allowed to track, i.e.
    // every truck with a Trip against one of the courier company's shipments.
    @GetMapping("/internal/by-shipments")
    public ResponseEntity<List<Trip>> byShipments(@RequestParam List<Long> shipmentIds) {
        return ResponseEntity.ok(tripRepository.findByShipmentIdIn(shipmentIds));
    }

    // "Trip creation" screen: pick truck + driver (+ optional shipment to fulfill)
    @PostMapping
    public ResponseEntity<Trip> create(@Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.create(request));
    }

    // Called directly by matching-service (Eureka name, not through the Gateway) the
    // instant a fleet manager accepts a booking — "fleet receives booking" becomes a
    // real, trackable Trip with no driver yet. No X-User-Id: this is service-to-service.
    @PostMapping("/internal")
    public ResponseEntity<Trip> createFromBooking(@Valid @RequestBody CreateTripFromBookingRequest request) {
        return ResponseEntity.ok(tripService.createFromBooking(request.truckId(), request.shipmentId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Trip> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getById(id));
    }

    // Driver App: "Start Trip" button
    @PatchMapping("/{id}/start")
    public ResponseEntity<Trip> startTrip(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        Long driverId = driverService.getByUserId(userId).getId();
        return ResponseEntity.ok(tripService.startTrip(id, driverId));
    }

    // Driver App: "Complete Delivery" button — ends the trip, frees the truck &
    // driver, and moves the shipment to DELIVERED (delivery completion time = endTime).
    @PatchMapping("/{id}/complete")
    public ResponseEntity<Trip> completeTrip(@PathVariable Long id, @RequestHeader("X-User-Id") Long userId) {
        Long driverId = driverService.getByUserId(userId).getId();
        return ResponseEntity.ok(tripService.completeTrip(id, driverId));
    }

    // "Driver assignment" screen: swap the driver on a not-yet-started trip
    @PatchMapping("/{id}/assign-driver")
    public ResponseEntity<Trip> assignDriver(@PathVariable Long id,
                                              @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.ok(tripService.assignDriver(id, request));
    }
}
