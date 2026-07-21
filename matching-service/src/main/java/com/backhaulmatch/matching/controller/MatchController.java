package com.backhaulmatch.matching.controller;

import com.backhaulmatch.matching.dto.MatchingDtos.CreateMatchRequestDto;
import com.backhaulmatch.matching.entity.MatchRequest;
import com.backhaulmatch.matching.entity.MatchResult;
import com.backhaulmatch.matching.service.MatchingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
@RequiredArgsConstructor
public class MatchController {

    private final MatchingService matchingService;

    // Courier Portal's "Request Backhaul Transport" button
    @PostMapping("/requests")
    public ResponseEntity<MatchRequest> createRequest(@RequestHeader("X-User-Id") Long userId,
                                                        @Valid @RequestBody CreateMatchRequestDto request) {
        return ResponseEntity.ok(matchingService.createAndRun(request.shipmentId(), userId));
    }

    @GetMapping("/requests/{id}")
    public ResponseEntity<MatchRequest> getRequest(@PathVariable Long id) {
        return ResponseEntity.ok(matchingService.getRequest(id));
    }

    // "4. MATCH RESULTS INTERFACE" — the ranked list of candidate trucks
    @GetMapping("/requests/{id}/results")
    public ResponseEntity<List<MatchResult>> getResults(@PathVariable Long id) {
        return ResponseEntity.ok(matchingService.getResults(id));
    }

    // Courier operator clicks "Select" on a recommended truck
    @PostMapping("/results/{id}/select")
    public ResponseEntity<MatchResult> selectResult(@PathVariable Long id) {
        return ResponseEntity.ok(matchingService.selectResult(id));
    }
}
