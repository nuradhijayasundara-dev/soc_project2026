package com.backhaulmatch.fleet.repository;

import com.backhaulmatch.fleet.entity.TruckAvailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TruckAvailabilityRepository extends JpaRepository<TruckAvailability, Long> {
    List<TruckAvailability> findByTruckId(Long truckId);
    List<TruckAvailability> findByStatus(TruckAvailability.Status status);
}
