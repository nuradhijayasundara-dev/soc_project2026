package com.backhaulmatch.matching.repository;

import com.backhaulmatch.matching.entity.MatchRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchRequestRepository extends JpaRepository<MatchRequest, Long> {
    List<MatchRequest> findByShipmentId(Long shipmentId);
    List<MatchRequest> findByRequestedByUserIdOrderByCreatedAtDesc(Long userId);
}
