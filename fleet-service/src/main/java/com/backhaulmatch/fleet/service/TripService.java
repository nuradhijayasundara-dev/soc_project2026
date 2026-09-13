package com.backhaulmatch.fleet.service;

import com.backhaulmatch.fleet.client.CourierServiceClient;
import com.backhaulmatch.fleet.dto.TripDtos.AssignDriverRequest;
import com.backhaulmatch.fleet.dto.TripDtos.CreateTripRequest;
import com.backhaulmatch.fleet.dto.TripDtos.DriverTripResponse;
import com.backhaulmatch.fleet.entity.Driver;
import com.backhaulmatch.fleet.entity.Trip;
import com.backhaulmatch.fleet.entity.Truck;
import com.backhaulmatch.fleet.repository.DriverRepository;
import com.backhaulmatch.fleet.repository.TripRepository;
import com.backhaulmatch.fleet.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class TripService {

    private final TripRepository tripRepository;
    private final TruckService truckService;
    private final DriverService driverService;
    private final CourierServiceClient courierServiceClient;
    private final TruckRepository truckRepository;
    private final DriverRepository driverRepository;

    public List<Trip> listForTrucks(List<Long> truckIds) {
        return tripRepository.findByTruckIdInOrderByStartTimeDesc(truckIds);
    }

    // Driver App: "which trips are assigned to me" — enriched with the truck no
    // and shipment details so the app can render the route without extra calls.
    public List<DriverTripResponse> listForDriver(Long driverId) {
        return tripRepository.findByDriverIdOrderByStartTimeDesc(driverId).stream()
                .map(this::toDriverTripResponse)
                .toList();
    }

    private DriverTripResponse toDriverTripResponse(Trip trip) {
        String truckNo = null;
        try {
            truckNo = truckService.getById(trip.getTruckId()).getTruckNo();
        } catch (Exception ignored) {
        }

        String shipmentCode = null, pickupLocation = null, destination = null, estDuration = null;
        Double pickupLat = null, pickupLng = null, destLat = null, destLng = null;
        Double weightKg = null;
        BigDecimal distanceKm = null;
        if (trip.getShipmentId() != null) {
            try {
                Map<String, Object> s = courierServiceClient.getShipment(trip.getShipmentId());
                shipmentCode = str(s.get("shipmentCode"));
                pickupLocation = str(s.get("pickupLocation"));
                destination = str(s.get("destination"));
                pickupLat = dbl(s.get("pickupLatitude"));
                pickupLng = dbl(s.get("pickupLongitude"));
                destLat = dbl(s.get("destinationLatitude"));
                destLng = dbl(s.get("destinationLongitude"));
                weightKg = dbl(s.get("weightKg"));
                distanceKm = s.get("distanceKm") == null ? null : new BigDecimal(s.get("distanceKm").toString());
                estDuration = str(s.get("estimatedDuration"));
            } catch (Exception e) {
                log.warn("Could not enrich trip {} with shipment {}: {}", trip.getId(), trip.getShipmentId(), e.getMessage());
            }
        }

        return new DriverTripResponse(
                trip.getId(), trip.getTruckId(), truckNo, trip.getDriverId(), trip.getShipmentId(),
                trip.getStatus().name(), trip.getStartTime(), trip.getEndTime(),
                shipmentCode, pickupLocation, destination, pickupLat, pickupLng, destLat, destLng,
                distanceKm, estDuration, weightKg
        );
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Double dbl(Object o) {
        return o == null ? null : Double.valueOf(o.toString());
    }

    public Trip getById(Long id) {
        return tripRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    }

    /** Driver App's "Start Trip" button. Only the assigned driver may start it. */
    public Trip startTrip(Long tripId, Long driverId) {
        Trip trip = getById(tripId);
        if (!trip.getDriverId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trip is not assigned to you");
        }
        if (trip.getStatus() != Trip.Status.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip has already started or finished");
        }
        trip.setStatus(Trip.Status.IN_PROGRESS);
        trip.setStartTime(LocalDateTime.now());
        Trip saved = tripRepository.save(trip);

        // Keep the courier's view in sync: a started trip means the shipment is
        // now in transit. Fire-and-forget so a courier-service hiccup can't block
        // the driver from starting the trip.
        if (saved.getShipmentId() != null) {
            try {
                courierServiceClient.updateShipmentStatus(saved.getShipmentId(), "IN_TRANSIT", "Trip started");
            } catch (Exception e) {
                log.warn("Could not mark shipment {} IN_TRANSIT after trip {} started: {}",
                        saved.getShipmentId(), tripId, e.getMessage());
            }
        }
        return saved;
    }

    /** Driver App's "Complete Delivery" button. Only the assigned driver, and only while in progress. */
    public Trip completeTrip(Long tripId, Long driverId) {
        Trip trip = getById(tripId);
        if (!trip.getDriverId().equals(driverId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This trip is not assigned to you");
        }
        if (trip.getStatus() != Trip.Status.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip can only be completed while it's in progress");
        }
        trip.setStatus(Trip.Status.COMPLETED);
        trip.setEndTime(LocalDateTime.now());
        Trip saved = tripRepository.save(trip);

        // Free the truck + the driver for the next trip.
        if (saved.getTruckId() != null) {
            Truck truck = truckService.getById(saved.getTruckId());
            truck.setStatus(Truck.Status.AVAILABLE);
            truckRepository.save(truck);
        }
        if (saved.getDriverId() != null) {
            driverService.setStatus(saved.getDriverId(), Driver.Status.AVAILABLE);
        }

        // Courier's view: shipment is delivered. Also records the delivery
        // completion time (trip.endTime) on the trip itself.
        if (saved.getShipmentId() != null) {
            try {
                courierServiceClient.updateShipmentStatus(saved.getShipmentId(), "DELIVERED", "Delivered");
            } catch (Exception e) {
                log.warn("Could not mark shipment {} DELIVERED after trip {} completed: {}",
                        saved.getShipmentId(), tripId, e.getMessage());
            }
        }
        return saved;
    }

    /**
     * Creates a Trip for a truck (+ optionally a driver), optionally against a
     * real shipment. If a shipmentId is supplied we validate it exists by
     * calling courier-service directly (service-to-service, bypassing the Gateway).
     * driverId may be null — a trip created automatically when a booking is
     * accepted doesn't have one yet; the fleet manager assigns it afterwards.
     */
    public Trip create(CreateTripRequest req) {
        Truck truck = truckService.getById(req.truckId());
        if (truck.getStatus() != Truck.Status.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Truck is not available");
        }

        Driver driver = null;
        if (req.driverId() != null) {
            driver = driverService.getById(req.driverId());
            if (driver.getStatus() != Driver.Status.AVAILABLE) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver is not available");
            }
        }

        if (req.shipmentId() != null) {
            courierServiceClient.getShipment(req.shipmentId()); // throws 400 if it doesn't exist
        }

        Trip trip = new Trip();
        trip.setTruckId(req.truckId());
        trip.setDriverId(req.driverId());
        trip.setShipmentId(req.shipmentId());
        trip.setStatus(Trip.Status.SCHEDULED);
        Trip saved = tripRepository.save(trip);

        // Persist the truck/driver status changes explicitly — outside a
        // transactional boundary, mutating the managed entities alone would
        // silently never flush to the database.
        truck.setStatus(Truck.Status.ON_TRIP);
        truckRepository.save(truck);
        if (driver != null) {
            driver.setStatus(Driver.Status.ON_TRIP);
            driverRepository.save(driver);
        }

        return saved;
    }

    /**
     * "Fleet receives booking" -> Trip created automatically the instant a
     * booking is accepted (called directly by matching-service, Eureka name,
     * no Gateway/JWT). Truck capacity was already reserved earlier when the
     * courier clicked "Accept Match" — this just gives the shipment a Trip to
     * track. No driver yet: the fleet manager assigns one via the existing
     * Trip list / "Driver Assignment" screen, and GPS tracking begins once
     * that driver starts the trip from the Driver App.
     */
    public Trip createFromBooking(Long truckId, Long shipmentId) {
        return create(new CreateTripRequest(truckId, null, shipmentId));
    }

    /** "Driver assignment" — reassign a different driver to an existing (not-yet-started) trip. */
    /** "Driver assignment" — assign (or reassign) a driver on an existing, not-yet-started trip. */
    public Trip assignDriver(Long tripId, AssignDriverRequest req) {
        Trip trip = getById(tripId);
        if (trip.getStatus() != Trip.Status.SCHEDULED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Can only reassign a driver before the trip starts");
        }
        Driver newDriver = driverService.getById(req.driverId());
        if (newDriver.getStatus() != Driver.Status.AVAILABLE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Driver is not available");
        }

        if (trip.getDriverId() != null) {
            driverService.setStatus(trip.getDriverId(), Driver.Status.AVAILABLE); // free the old driver
        }
        trip.setDriverId(newDriver.getId());
        driverService.setStatus(newDriver.getId(), Driver.Status.ON_TRIP);

        Trip saved = tripRepository.save(trip);

        // Courier's view: a booked shipment now has a driver behind the wheel.
        if (saved.getShipmentId() != null) {
            try {
                courierServiceClient.updateShipmentStatus(saved.getShipmentId(), "DRIVER_ASSIGNED", "Driver assigned");
            } catch (Exception e) {
                log.warn("Could not mark shipment {} DRIVER_ASSIGNED: {}", saved.getShipmentId(), e.getMessage());
            }
        }

        return saved;
    }
}
