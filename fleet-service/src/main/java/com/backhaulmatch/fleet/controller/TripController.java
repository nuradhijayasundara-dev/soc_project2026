package com.backhaulmatch.fleet.controller;

import com.backhaulmatch.fleet.dto.TripDtos.AssignDriverRequest;
import com.backhaulmatch.fleet.dto.TripDtos.CreateTripRequest;
import com.backhaulmatch.fleet.entity.Trip;
import com.backhaulmatch.fleet.repository.TruckRepository;
import com.backhaulmatch.fleet.service.FleetCompanyService;
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
    private final TruckRepository truckRepository;

    @GetMapping
    public ResponseEntity<List<Trip>> list(@RequestHeader("X-User-Id") Long userId) {
        Long companyId = companyService.resolveCompanyId(userId);
        List<Long> truckIds = truckRepository.findByFleetCompanyId(companyId).stream()
                .map(t -> t.getId()).collect(Collectors.toList());
        return ResponseEntity.ok(tripService.listForTrucks(truckIds));
    }

    // "Trip creation" screen: pick truck + driver (+ optional shipment to fulfill)
    @PostMapping
    public ResponseEntity<Trip> create(@Valid @RequestBody CreateTripRequest request) {
        return ResponseEntity.ok(tripService.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Trip> getById(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getById(id));
    }

    // "Driver assignment" screen: swap the driver on a not-yet-started trip
    @PatchMapping("/{id}/assign-driver")
    public ResponseEntity<Trip> assignDriver(@PathVariable Long id,
                                              @Valid @RequestBody AssignDriverRequest request) {
        return ResponseEntity.ok(tripService.assignDriver(id, request));
    }
}
