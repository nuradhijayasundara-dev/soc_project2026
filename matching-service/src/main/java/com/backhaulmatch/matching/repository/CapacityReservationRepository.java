package com.backhaulmatch.matching.repository;

import com.backhaulmatch.matching.entity.CapacityReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CapacityReservationRepository extends JpaRepository<CapacityReservation, Long> {
    Optional<CapacityReservation> findByMatchResultId(Long matchResultId);
}
