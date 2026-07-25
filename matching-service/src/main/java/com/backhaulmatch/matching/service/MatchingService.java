package com.backhaulmatch.matching.service;

import com.backhaulmatch.matching.client.CourierServiceClient;
import com.backhaulmatch.matching.client.FleetServiceClient;
import com.backhaulmatch.matching.client.NotificationClient;
import com.backhaulmatch.matching.dto.MatchingDtos.AvailabilityCandidate;
import com.backhaulmatch.matching.entity.CapacityReservation;
import com.backhaulmatch.matching.entity.MatchRequest;
import com.backhaulmatch.matching.entity.MatchResult;
import com.backhaulmatch.matching.repository.CapacityReservationRepository;
import com.backhaulmatch.matching.repository.MatchRequestRepository;
import com.backhaulmatch.matching.repository.MatchResultRepository;
import com.backhaulmatch.matching.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
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
    private final CapacityReservationRepository reservationRepository;
    private final CourierServiceClient courierServiceClient;
    private final FleetServiceClient fleetServiceClient;
    private final NotificationClient notificationClient;

    // Placeholder pricing until the Pricing Service exists — LKR base fare + LKR/km,
    // falling back to a flat per-ton rate only when the route isn't in our city table.
    private static final BigDecimal BASE_FARE = new BigDecimal("1000");
    private static final BigDecimal RATE_PER_KM = new BigDecimal("150");
    private static final BigDecimal RATE_PER_TON_FALLBACK = new BigDecimal("500");

    // Match Score weights: 1 km of route deviation "costs" as much as 1 ton of
    // wasted capacity. Tune these independently once real data shows which
    // factor should dominate.
    private static final double DISTANCE_WEIGHT = 1.0;
    private static final double CAPACITY_WEIGHT = 1.0;

    // A truck whose posted route is more than this many km (combined pickup +
    // destination deviation) away from the shipment's actual route isn't a
    // realistic backhaul candidate — drop it rather than just scoring it low.
    private static final double MAX_ROUTE_DEVIATION_KM = 80.0;

    /**
     * The Matching Engine's core flow:
     *   1. Pull the shipment (route + weight) from courier-service.
     *   2. Create a MatchRequest snapshot.
     *   3. Ask fleet-service for every backhaul availability with enough spare
     *      capacity (route is NOT filtered server-side anymore — see below).
     *   4. Score and rank the candidates by distance + capacity fit.
     *   5. Persist the ranked list as MatchResult rows and return them.
     *   6. If anything was found, notify the requester — "Match found".
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
        } else {
            notificationClient.notify(
                    saved.getRequestedByUserId(),
                    "MATCH_FOUND",
                    "Backhaul trucks found",
                    String.format("%d truck(s) available for your shipment from %s to %s.",
                            results.size(), saved.getPickupLocation(), saved.getDestination()),
                    saved.getId()
            );
        }
        return saved;
    }

    /**
     * Capacity matching + Distance Calculation: fleet-service's search is now
     * called with NO route filter (from/to = null) so it returns every AVAILABLE
     * slot with enough capacity across all companies; matching-service itself
     * computes how far each candidate's posted route deviates from the
     * shipment's actual pickup/destination, drops anything too far off, and
     * ranks what's left by a combined distance + capacity-waste score.
     */
    private List<MatchResult> runMatching(MatchRequest request) {
        BigDecimal requiredTon = toTons(request.getWeightKg());

        List<AvailabilityCandidate> candidates = fleetServiceClient.searchAvailability(null, null, requiredTon);

        List<MatchResult> results = candidates.stream()
                .map(c -> toScoredResult(request, c, requiredTon))
                .filter(r -> r.getDistanceKm() <= MAX_ROUTE_DEVIATION_KM)
                .sorted(Comparator.comparingDouble(MatchResult::getMatchScore))
                .limit(10)
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
        result.setEstimatedCost(estimateCost(request, c));

        double distanceKm = DistanceCalculator.routeDeviationKm(
                request.getPickupLocation(), request.getDestination(), c.routeFrom(), c.routeTo());
        result.setDistanceKm(distanceKm);

        // Match Score: lower is better. Combines how far off-route the truck is
        // with how much capacity would go to waste — a truck with a perfect
        // route but way too much spare capacity can still lose to a nearby
        // truck sized almost exactly right, and vice versa.
        double wastedCapacity = Math.max(c.availableCapacityTon().subtract(requiredTon).doubleValue(), 0);
        double score = (distanceKm * DISTANCE_WEIGHT) + (wastedCapacity * CAPACITY_WEIGHT);
        result.setMatchScore(score);

        result.setStatus(MatchResult.Status.RECOMMENDED);
        return result;
    }

    /**
     * Base fare + distance-based rate for the shipment's actual pickup-to-destination
     * leg (the truck's own posted route doesn't change what carrying THIS shipment
     * costs). Falls back to a flat per-ton rate if either city isn't in
     * DistanceCalculator's table, so cost is never left blank.
     */
    private BigDecimal estimateCost(MatchRequest request, AvailabilityCandidate candidate) {
        Double routeKm = DistanceCalculator.distanceBetweenCities(request.getPickupLocation(), request.getDestination());
        if (routeKm != null) {
            return BASE_FARE.add(BigDecimal.valueOf(routeKm).multiply(RATE_PER_KM)).setScale(2, RoundingMode.HALF_UP);
        }
        return candidate.availableCapacityTon().multiply(RATE_PER_TON_FALLBACK).setScale(2, RoundingMode.HALF_UP);
    }

    public List<MatchResult> getResults(Long matchRequestId) {
        return matchResultRepository.findByMatchRequestIdOrderByMatchScoreAsc(matchRequestId);
    }

    public MatchRequest getRequest(Long id) {
        return matchRequestRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match request not found"));
    }

    private MatchResult getResult(Long id) {
        return matchResultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match result not found"));
    }

    /**
     * "Courier Accepts" -> "Booking Created" -> "Truck Capacity Reserved", all in one step:
     * re-verifies capacity and immediately reserves the slot in fleet-service (BOOKED),
     * records a CapacityReservation, marks the result PENDING_CONFIRMATION, and sends
     * the fleet manager a "booking request" notification.
     */
    public MatchResult acceptMatch(Long matchResultId, Long courierUserId) {
        MatchResult result = getResult(matchResultId);
        if (result.getStatus() != MatchResult.Status.RECOMMENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This candidate is no longer available");
        }

        MatchRequest request = getRequest(result.getMatchRequestId());
        if (!request.getRequestedByUserId().equals(courierUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This match request isn't yours");
        }

        BigDecimal requiredTon = toTons(request.getWeightKg());

        // Capacity Verification + reservation, atomically as far as fleet-service is concerned.
        try {
            fleetServiceClient.reserveCapacity(result.getTruckAvailabilityId(), requiredTon);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This truck's capacity was just taken by another shipment — pick a different candidate");
        }

        result.setStatus(MatchResult.Status.PENDING_CONFIRMATION);
        MatchResult saved = matchResultRepository.save(result);

        CapacityReservation reservation = new CapacityReservation();
        reservation.setMatchResultId(result.getId());
        reservation.setTruckAvailabilityId(result.getTruckAvailabilityId());
        reservation.setReservedCapacityTon(requiredTon);
        reservation.setStatus(CapacityReservation.Status.RESERVED);
        reservationRepository.save(reservation);

        Long fleetOwnerUserId = fleetServiceClient.getCompanyOwnerUserId(result.getFleetCompanyId());
        if (fleetOwnerUserId != null) {
            notificationClient.notify(
                    fleetOwnerUserId,
                    "BOOKING_REQUESTED",
                    "New booking request",
                    String.format("A courier wants to book truck %s for the %s -> %s route. Capacity is reserved pending your decision.",
                            result.getTruckNo(), request.getPickupLocation(), request.getDestination()),
                    result.getId()
            );
        }

        return saved;
    }

    /** "Booking Requests" page in the Fleet Portal: everything awaiting this company's decision. */
    public List<MatchResult> getPendingBookingsForUser(Long fleetManagerUserId) {
        Long companyId = fleetServiceClient.getCompanyIdForUser(fleetManagerUserId);
        return matchResultRepository.findByFleetCompanyIdAndStatusOrderByCreatedAtDesc(
                companyId, MatchResult.Status.PENDING_CONFIRMATION);
    }

    /** Fleet manager accepts — the reservation becomes permanent, siblings are auto-rejected. */
    public MatchResult acceptBooking(Long matchResultId) {
        MatchResult chosen = getResult(matchResultId);
        if (chosen.getStatus() != MatchResult.Status.PENDING_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking request is no longer pending");
        }
        chosen.setStatus(MatchResult.Status.ACCEPTED);
        matchResultRepository.save(chosen);

        reservationRepository.findByMatchResultId(chosen.getId()).ifPresent(r -> {
            r.setStatus(CapacityReservation.Status.CONFIRMED);
            reservationRepository.save(r);
        });

        List<MatchResult> siblings = matchResultRepository.findByMatchRequestIdOrderByMatchScoreAsc(chosen.getMatchRequestId());
        for (MatchResult sibling : siblings) {
            if (!sibling.getId().equals(chosen.getId()) && sibling.getStatus() != MatchResult.Status.REJECTED) {
                sibling.setStatus(MatchResult.Status.REJECTED);
            }
        }
        matchResultRepository.saveAll(siblings);

        MatchRequest request = getRequest(chosen.getMatchRequestId());
        request.setStatus(MatchRequest.Status.MATCHED);
        matchRequestRepository.save(request);

        notificationClient.notify(
                request.getRequestedByUserId(),
                "BOOKING_ACCEPTED",
                "Booking accepted",
                String.format("Truck %s confirmed for your shipment from %s to %s.",
                        chosen.getTruckNo(), request.getPickupLocation(), request.getDestination()),
                request.getShipmentId()
        );

        return chosen;
    }

    /** Fleet manager declines — release the reserved capacity, notify the courier, try again. */
    public MatchResult rejectBooking(Long matchResultId) {
        MatchResult result = getResult(matchResultId);
        if (result.getStatus() != MatchResult.Status.PENDING_CONFIRMATION) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking request is no longer pending");
        }
        result.setStatus(MatchResult.Status.REJECTED);
        matchResultRepository.save(result);

        fleetServiceClient.releaseCapacity(result.getTruckAvailabilityId());
        reservationRepository.findByMatchResultId(result.getId()).ifPresent(r -> {
            r.setStatus(CapacityReservation.Status.RELEASED);
            reservationRepository.save(r);
        });

        MatchRequest request = getRequest(result.getMatchRequestId());
        notificationClient.notify(
                request.getRequestedByUserId(),
                "BOOKING_REJECTED",
                "Booking declined",
                String.format("Truck %s declined your booking request for the %s -> %s shipment. " +
                                "Try another recommended truck.",
                        result.getTruckNo(), request.getPickupLocation(), request.getDestination()),
                request.getShipmentId()
        );

        boolean anyLeft = matchResultRepository.findByMatchRequestIdOrderByMatchScoreAsc(request.getId()).stream()
                .anyMatch(r -> r.getStatus() == MatchResult.Status.RECOMMENDED || r.getStatus() == MatchResult.Status.PENDING_CONFIRMATION);
        if (!anyLeft) {
            request.setStatus(MatchRequest.Status.NO_MATCH);
            matchRequestRepository.save(request);
        }

        return result;
    }

    private BigDecimal toTons(BigDecimal weightKg) {
        return weightKg == null ? BigDecimal.ZERO : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
    }
}
