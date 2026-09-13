package com.backhaulmatch.fleet.repository;

import com.backhaulmatch.fleet.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByTruckIdInOrderByStartTimeDesc(List<Long> truckIds);
    List<Trip> findByDriverIdOrderByStartTimeDesc(Long driverId);

    // Internal (service-to-service): which trucks haul a set of shipment ids —
    // used by gps-service to scope a courier's GPS access to their own shipments.
    List<Trip> findByShipmentIdIn(List<Long> shipmentIds);

    // "Fleet Reports" — total trips, via a plain COUNT query.
    long countByTruckIdIn(List<Long> truckIds);
}
