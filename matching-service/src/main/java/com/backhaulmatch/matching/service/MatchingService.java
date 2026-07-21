package com.backhaulmatch.matching.service;

import com.backhaulmatch.matching.client.CourierServiceClient;
import com.backhaulmatch.matching.client.FleetServiceClient;
import com.backhaulmatch.matching.dto.MatchingDtos.AvailabilityCandidate;
import com.backhaulmatch.matching.entity.MatchRequest;
import com.backhaulmatch.matching.entity.MatchResult;
import com.backhaulmatch.matching.repository.MatchRequestRepository;
import com.backhaulmatch.matching.repository.MatchResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MatchingService {

    private final MatchRequestRepository matchRequestRepository;
    private final MatchResultRepository matchResultRepository;
    private final CourierServiceClient courierServiceClient;
    private final FleetServiceClient fleetServiceClient;

    // Placeholder rate until the Pricing Service exists — LKR per ton, flat.
    private static final BigDecimal RATE_PER_TON = new BigDecimal("500");

    /**
     * The Matching Engine's core flow:
     *   1. Pull the shipment (route + weight) from courier-service.
     *   2. Create a MatchRequest snapshot.
     *   3. Ask fleet-service for every backhaul availability on that route with
     *      enough spare capacity.
     *   4. Score and rank the candidates (capacity matching logic below).
     *   5. Persist the ranked list as MatchResult rows and return them.
     */
    public MatchRequest createAndRun(Long shipmentId, Long userId) {
        Map<String, Object> shipment = courierServiceClient.getShipment(shipmentId);

        MatchRequest request = new MatchRequest();
        request.setShipmentId(shipmentId);
        request.setRequestedByUserId(userId);
        request.setPickupLocation((String) shipment.get("pickupLocation"));
        request.setDestination((String) shipment.get("destination"));
        Object weight = shipment.get("weightKg");
        request.setWeightKg(weight == null ? BigDecimal.ZERO : new BigDecimal(weight.toString()));
        request.setStatus(MatchRequest.Status.PENDING);
        MatchRequest saved = matchRequestRepository.save(request);

        List<MatchResult> results = runMatching(saved);
        if (results.isEmpty()) {
            saved.setStatus(MatchRequest.Status.NO_MATCH);
            matchRequestRepository.save(saved);
        }
        return saved;
    }

    /** Capacity matching logic: filters + scores candidate backhaul availability. */
    private List<MatchResult> runMatching(MatchRequest request) {
        BigDecimal requiredTon = request.getWeightKg() == null
                ? BigDecimal.ZERO
                : request.getWeightKg().divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);

        List<AvailabilityCandidate> candidates = fleetServiceClient.searchAvailability(
                request.getPickupLocation(), request.getDestination(), requiredTon);

        List<MatchResult> results = candidates.stream()
                .map(c -> toScoredResult(request, c, requiredTon))
                // best-fit first: least wasted capacity wins over a truck that's way oversized
                .sorted(Comparator.comparingDouble(MatchResult::getMatchScore))
                .limit(10) // cap the recommendation list, same as the mockup's 3 example cards
                .toList();

        return matchResultRepository.saveAll(results);
    }

    private MatchResult toScoredResult(MatchRequest request, AvailabilityCandidate c, BigDecimal requiredTon) {
        MatchResult result = new MatchResult();
        result.setMatchRequestId(request.getId());
        result.setTruckAvailabilityId(c.id());
        result.setTruckId(c.truckId());
        result.setFleetCompanyId(c.fleetCompanyId());
        result.setTruckNo(c.truckNo());
        result.setAvailableCapacityTon(c.availableCapacityTon());
        result.setRouteFrom(c.routeFrom());
        result.setRouteTo(c.routeTo());
        result.setEstimatedCost(c.availableCapacityTon().multiply(RATE_PER_TON).setScale(2, RoundingMode.HALF_UP));

        // Score = wasted capacity (ton) — a truck with 4.5 ton free for a 2 ton
        // shipment scores worse than one with 2.5 ton free. Lower is better.
        // (Route is already an exact match by the time it reaches here, since
        // fleet-service's search only returns candidates on the same lane.)
        double wastedCapacity = c.availableCapacityTon().subtract(requiredTon).doubleValue();
        result.setMatchScore(Math.max(wastedCapacity, 0));
        result.setStatus(MatchResult.Status.RECOMMENDED);
        return result;
    }

    public List<MatchResult> getResults(Long matchRequestId) {
        return matchResultRepository.findByMatchRequestIdOrderByMatchScoreAsc(matchRequestId);
    }

    public MatchRequest getRequest(Long id) {
        return matchRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match request not found"));
    }

    /** Courier operator picks a recommended truck — marks it selected, rejects the rest. */
    public MatchResult selectResult(Long matchResultId) {
        MatchResult chosen = matchResultRepository.findById(matchResultId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match result not found"));

        List<MatchResult> siblings = matchResultRepository.findByMatchRequestIdOrderByMatchScoreAsc(chosen.getMatchRequestId());
        for (MatchResult r : siblings) {
            r.setStatus(r.getId().equals(chosen.getId()) ? MatchResult.Status.SELECTED : MatchResult.Status.REJECTED);
        }
        matchResultRepository.saveAll(siblings);

        MatchRequest request = getRequest(chosen.getMatchRequestId());
        request.setStatus(MatchRequest.Status.MATCHED);
        matchRequestRepository.save(request);

        return chosen;
    }
}
