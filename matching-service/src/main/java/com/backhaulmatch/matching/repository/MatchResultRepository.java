package com.backhaulmatch.matching.repository;

import com.backhaulmatch.matching.entity.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {
    List<MatchResult> findByMatchRequestIdOrderByMatchScoreAsc(Long matchRequestId);
}
