package com.backhaulmatch.fleet.repository;

import com.backhaulmatch.fleet.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TripRepository extends JpaRepository<Trip, Long> {
    List<Trip> findByTruckIdInOrderByStartTimeDesc(List<Long> truckIds);
}
