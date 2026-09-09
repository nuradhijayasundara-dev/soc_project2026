package com.backhaulmatch.matching.service;

import com.backhaulmatch.matching.client.AdminSettingsClient;
import com.backhaulmatch.matching.client.CourierServiceClient;
import com.backhaulmatch.matching.client.FleetServiceClient;
import com.backhaulmatch.matching.client.NotificationClient;
import com.backhaulmatch.matching.client.PaymentServiceClient;
import com.backhaulmatch.matching.dto.MatchingDtos.AvailabilityCandidate;
import com.backhaulmatch.matching.entity.CapacityReservation;
import com.backhaulmatch.matching.entity.MatchRequest;
import com.backhaulmatch.matching.entity.MatchResult;
import com.backhaulmatch.matching.repository.CapacityReservationRepository;
import com.backhaulmatch.matching.repository.MatchRequestRepository;
import com.backhaulmatch.matching.repository.MatchResultRepository;
import com.backhaulmatch.matching.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class MatchingService {

    private final MatchRequestRepository matchRequestRepository;
    private final MatchResultRepository matchResultRepository;
    private final CapacityReservationRepository reservationRepository;
    private final CourierServiceClient courierServiceClient;
    private final FleetServiceClient fleetServiceClient;
    private final NotificationClient notificationClient;
    private final PaymentServiceClient paymentServiceClient;
    private final AdminSettingsClient adminSettingsClient;
    private final DistanceCalculator distanceCalculator;
    private final MatchScorer matchScorer;

    // Defaults, refreshed from admin-service's System Settings at the start of
    // each matching run (see loadPreferences) — pricing rules the admin can
    // change from the Admin Portal.
    private BigDecimal baseFare = new BigDecimal("1000");
    private BigDecimal ratePerKm = new BigDecimal("150");
    private BigDecimal ratePerTonFallback = new BigDecimal("500");

    // Absolute cap on how long a WAITING_FOR_MATCH request keeps looking. After
    // this, waiting is pointless even if the pickup time is far in the future or
    // wasn't set at all — the request (and shipment) settle as NO_MATCH and the
    // courier is notified, so nothing keeps looping forever.
    private static final long WAITING_MAX_HOURS = 72;

    private void loadPreferences() {
        try {
            Map<String, String> settings = adminSettingsClient.getSettings();
            baseFare = decimalSetting(settings, "pricing.baseFare", "1000");
            ratePerKm = decimalSetting(settings, "pricing.ratePerKm", "150");
            ratePerTonFallback = decimalSetting(settings, "pricing.ratePerTonFallback", "500");
        } catch (Exception e) {
            // keep compiled-in defaults on any hiccup
        }
    }

    private static BigDecimal decimalSetting(Map<String, String> settings, String key, String fallback) {
        return new BigDecimal(settings.getOrDefault(key, fallback));
    }

    /**
     * The Matching Engine's core flow (TASK 8 — the initial Courier matching flow):
     *   1. Pull the shipment (route + weight) from courier-service.
     *   2. Create a MatchRequest snapshot.
     *   3. Validate the shipment BEFORE any side-effects.
     *   4. Ask fleet-service for every backhaul availability with enough spare
     *      capacity (route is NOT filtered server-side — see below).
     *   5. Evaluate EVERY candidate with the single common matching algorithm
     *      (MatchScorer.evaluateVehicleForShipment), never taking the first row
     *      from the database.
     *   6. Select the best VALID vehicle — the highest scorer — and auto-assign
     *      it: reserve its capacity atomically (see assignBestMatch), notify the
     *      fleet manager, and flag it as the best match.
     *   7. Persist the ranked list as MatchResult rows (best reserved, siblings
     *      left RECOMMENDED as fallbacks) and return the request.
     *   8. If nothing suitable existed, the shipment becomes WAITING_FOR_MATCH —
     *      kept active, NO MatchResult, NO NO_MATCH, and NO re-submission
     *      required: the fleet-event trigger + scheduled rechecker will re-run
     *      the same matching for it automatically.
     */
    public synchronized MatchRequest createAndRun(Long shipmentId, Long userId) {
        loadPreferences();
        Map<String, Object> shipment = courierServiceClient.getShipment(shipmentId);

        // Validate BEFORE any side-effects (status change, MatchRequest creation).
        // Invalid requests must NOT become WAITING_FOR_MATCH — they are rejected
        // immediately with a proper error listing every problem found.
        validateShipmentForMatching(shipment);

        // Duplicate-request guard (TASK 9): if this shipment already has a live
        // request (anything not CANCELLED — e.g. a double-click on the button or a
        // concurrent retry), return that request instead of spawning a second
        // matching flow. Only one request per shipment can be running at once.
        java.util.Optional<MatchRequest> liveRequest =
                matchRequestRepository.findFirstByShipmentIdAndStatusNot(shipmentId, MatchRequest.Status.CANCELLED);
        if (liveRequest.isPresent()) {
            return liveRequest.get();
        }

        courierServiceClient.updateShipmentStatus(shipmentId, "MATCHING", null);

        MatchRequest request = new MatchRequest();
        request.setShipmentId(shipmentId);
        request.setRequestedByUserId(userId);
        request.setPickupLocation((String) shipment.get("pickupLocation"));
        request.setDestination((String) shipment.get("destination"));
        Object weight = shipment.get("weightKg");
        request.setWeightKg(weight == null ? BigDecimal.ZERO : new BigDecimal(weight.toString()));
        request.setRequiredVehicleType((String) shipment.get("requiredVehicleType"));
        request.setPriority((String) shipment.get("priority"));
        request.setPickupDatetime(parseDateTime(ofString(shipment.get("pickupDatetime"))));
        request.setStatus(MatchRequest.Status.PENDING);
        MatchRequest saved = matchRequestRepository.save(request);

        List<MatchResult> results = runMatching(saved);
        if (results.isEmpty()) {
            // No truck right now — DON'T burn the shipment. It stays active and
            // waits. The fleet-publish trigger + the scheduled rechecker will
            // keep looking and flip it to MATCH_FOUND the moment a fit appears.
            settleEmpty(saved);
        } else {
            // A suitable vehicle exists — auto-assign it. The ranked list is
            // walked highest-scoring first and the first truck whose capacity is
            // still free is reserved atomically (see assignBestMatch); every
            // remaining candidate stays RECOMMENDED as a fallback the courier /
            // fleet accept-reject flows can still use.
            MatchResult assigned = assignBestMatch(saved, results);
            if (assigned == null) {
                // Every candidate was grabbed by another shipment in the instant
                // between scoring and reservation — revert to hunting rather than
                // leave the courier with stale results.
                settleEmpty(saved);
                return saved;
            }
            settleMatched(saved, assigned);
        }
        return saved;
    }

    /**
     * "Try Again" on a NO_MATCH (or still-searching) result screen: retires the
     * old recommendations and asks the engine to re-run for the same shipment,
     * so the courier doesn't have to recreate the shipment to search again.
     */
    public MatchRequest rerun(Long matchRequestId, Long userId) {
        MatchRequest request = getRequest(matchRequestId);
        if (!request.getRequestedByUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This match request isn't yours");
        }
        if (request.getStatus() != MatchRequest.Status.NO_MATCH
                && request.getStatus() != MatchRequest.Status.PENDING
                && request.getStatus() != MatchRequest.Status.WAITING_FOR_MATCH) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot re-run a request that's already been acted on");
        }

        List<MatchResult> stale = matchResultRepository.findByMatchRequestIdOrderByMatchScoreDesc(request.getId());
        stale.forEach(r -> {
            if (r.getStatus() == MatchResult.Status.RECOMMENDED) r.setStatus(MatchResult.Status.REJECTED);
        });
        matchResultRepository.saveAll(stale);

        loadPreferences();

        // Re-validate the shipment before re-running — it may have been
        // cancelled, matched, or had bad data since the original request.
        Map<String, Object> shipment = courierServiceClient.getShipment(request.getShipmentId());
        validateShipmentForMatching(shipment);

        List<MatchResult> results = runMatching(request);
        if (results.isEmpty()) {
            settleEmpty(request);
        } else {
            // Same auto-assignment as the initial flow: walk the ranked list
            // best-first, reserve the first truck whose capacity is still free.
            MatchResult assigned = assignBestMatch(request, results);
            if (assigned == null) {
                settleEmpty(request);
                return matchRequestRepository.save(request);
            }
            settleMatched(request, assigned);
        }
        return matchRequestRepository.save(request);
    }

    /**
     * The core matching flow, used by EVERY entry point — initial request (createAndRun),
     * rerun, the waiting recheck, new-vehicle events and scheduled retries all land here.
     *
     * <p>Every candidate is evaluated through ONE method —
     * {@link MatchScorer#evaluateVehicleForShipment(MatchRequest, AvailabilityCandidate)} —
     * which runs the complete algorithm (validate shipment, validate vehicle,
     * capacity, vehicle type, date/time, OSRM route, pickup/delivery corridor,
     * pickup-before-delivery, 0–100 score, minimum-score gate) and returns the
     * scored {@link MatchScorer.Evaluation}. That same method is the only
     * matcher used by every flow — initial, rerun, waiting recheck, new-vehicle
     * events and the scheduler — so a truck is decided identically however the
     * run was triggered. Credit checks / deduplication are orchestrated here;
     * the decision itself lives in the scorer.
     */
    private List<MatchResult> runMatching(MatchRequest request) {
        // Dedupe + refresh: a truck availability must only ever carry ONE active
        // (RECOMMENDED) MatchResult per request. Any previous RECOMMENDED rows are
        // retired first (kept as REJECTED history), and the ids that still have an
        // active/busy result (PENDING_CONFIRMATION / ACCEPTED) are skipped so a
        // truck that's mid-booking is never silently re-recommended. This is what
        // keeps repeated rechecks and reruns from piling up duplicate matches.
        java.util.Set<Long> busyIds = new java.util.HashSet<>();
        List<MatchResult> existing = matchResultRepository.findByMatchRequestIdOrderByMatchScoreDesc(request.getId());
        for (MatchResult r : existing) {
            if (r.getStatus() == MatchResult.Status.PENDING_CONFIRMATION
                    || r.getStatus() == MatchResult.Status.ACCEPTED) {
                busyIds.add(r.getTruckAvailabilityId());
            }
            if (r.getStatus() == MatchResult.Status.RECOMMENDED) {
                r.setStatus(MatchResult.Status.REJECTED);
            }
        }
        matchResultRepository.saveAll(existing);

        BigDecimal requiredTon = toTons(request.getWeightKg());

        List<AvailabilityCandidate> candidates = fleetServiceClient.searchAvailability(null, null, requiredTon);

        // Cross-request duplicate prevention: any of these candidate trucks that
        // are mid-booking (PENDING_CONFIRMATION) or already booked (ACCEPTED) for a
        // DIFFERENT shipment are excluded here too — a slot is never recommended to
        // two requests at once, even though fleet-service's atomic BOOKED flag is
        // the final backstop the instant a courier clicks "Book This Truck".
        if (!candidates.isEmpty()) {
            List<Long> candidateIds = candidates.stream().map(AvailabilityCandidate::id).toList();
            List<MatchResult> globallyBusy = matchResultRepository.findByTruckAvailabilityIdInAndStatusIn(
                    candidateIds,
                    List.of(MatchResult.Status.PENDING_CONFIRMATION, MatchResult.Status.ACCEPTED));
            globallyBusy.forEach(b -> busyIds.add(b.getTruckAvailabilityId()));
        }

        List<MatchResult> results = candidates.stream()
                .filter(c -> !busyIds.contains(c.id()))
                .map(c -> new ScoredCandidate(c, matchScorer.evaluateVehicleForShipment(request, c)))
                .filter(sc -> sc.score().valid())
                .sorted(Comparator.comparingDouble((ScoredCandidate sc) -> sc.score().score()).reversed())
                .limit(10)
                .map(sc -> toScoredResult(request, sc))
                .toList();

        // Best-match selection: the single top-scoring candidate is the engine's
        // automatic match for this request — flagged so the UI/API can surface
        // "Fit #1 + Best Match" distinctly from the alternatives.
        if (!results.isEmpty()) {
            results.get(0).setIsBestMatch(true);
        }

        return matchResultRepository.saveAll(results);
    }

    /**
     * The heart of the "Waiting for Match" feature (TASKS 10-14). Called after a
     * fleet manager publishes new availability (immediate) and on a schedule
     * (safety net). Re-runs the engine for every still-WAITING request:
     * <ul>
     *   <li>expired requests settle as NO_MATCH;</li>
     *   <li>shipments that turned invalid (cancelled / booked elsewhere / expired)
     *       are cancelled — the automatic matcher ignores them;</li>
     *   <li>a truck that now fits is auto-assigned: MatchResult created, the
     *       request/shipment flip WAITING_FOR_MATCH → MATCH_FOUND exactly once,
     *       the courier is notified exactly once;</li>
     *   <li>no truck yet, or a transient failure (courier/fleet/OSRM), leaves the
     *       shipment WAITING_FOR_MATCH so the next pass retries it — a service
     *       outage is NEVER treated as "no match".</li>
     * </ul>
     *
     * <p>{@code synchronized} so the fleet-publish trigger and the scheduled tick
     * can never process the same request concurrently — that's what guarantees a
     * request is only ever transitioned and notified once (TASKS 9 &amp; 12).
     */
    public synchronized int recheckWaitingMatches() {
        loadPreferences();
        List<MatchRequest> waiting = matchRequestRepository.findByStatus(MatchRequest.Status.WAITING_FOR_MATCH);
        int newlyMatched = 0;
        for (MatchRequest request : waiting) {
            if (isExpired(request)) {
                expireWait(request);
                continue;
            }

            try {
                // Re-validate the shipment before every automatic re-check — it may
                // have been cancelled, matched, or had bad data since the original
                // request. Invalid shipments are cancelled (not left looping forever);
                // OSRM failures don't cause this path (they degrade to haversine
                // inside DistanceCalculator, so no exception is thrown).
                Map<String, Object> shipment = courierServiceClient.getShipment(request.getShipmentId());
                validateShipmentForMatching(shipment);

                List<MatchResult> results = runMatching(request);
                if (results.isEmpty()) {
                    // Still no suitable truck — the request stays WAITING_FOR_MATCH.
                    continue;
                }

                // A truck now fits. Same auto-assignment as the initial flow: reserve
                // the best one whose capacity is still free (others stay RECOMMENDED
                // as fallbacks).
                MatchResult assigned = assignBestMatch(request, results);
                if (assigned == null) {
                    // Every candidate was just taken by another shipment before we
                    // could reserve it — stay WAITING and retry next pass rather than
                    // notify the courier about a truck that's already gone.
                    continue;
                }

                settleMatched(request, assigned);
                newlyMatched++;
            } catch (MatchingValidationException e) {
                // The shipment itself is no longer a valid match candidate — cancel
                // this request. Its shipment status is only touched if the shipment
                // is still in an active hunting state.
                Map<String, Object> shipment = null;
                try {
                    shipment = courierServiceClient.getShipment(request.getShipmentId());
                } catch (Exception ignored) {
                    // status is read best-effort; the request still gets cancelled
                }
                cancelInvalidWaiting(request, shipment == null ? null : ofString(shipment.get("status")));
            } catch (Exception e) {
                // Transient failure (courier/fleet/OSRM temporarily unavailable):
                // leave the shipment WAITING_FOR_MATCH and let the next scheduled
                // pass retry it. Never settle a valid shipment as NO_MATCH because
                // of an infrastructure hiccup (TASK 14).
                log.warn("Recheck of request {} skipped (transient error, will retry): {}",
                        request.getId(), e.getMessage());
            }
        }
        return newlyMatched;
    }

    /**
     * A waiting request gives up in either case: (a) its pickup time has passed
     * with no truck found, or (b) it has been waiting longer than
     * {@link #WAITING_MAX_HOURS} — so a request with no pickup datetime (or a
     * far future one) still can't loop forever.
     */
    private boolean isExpired(MatchRequest request) {
        LocalDateTime pickup = request.getPickupDatetime();
        if (pickup != null && pickup.isBefore(LocalDateTime.now())) return true;
        LocalDateTime createdAt = request.getCreatedAt();
        return createdAt != null
                && createdAt.plusHours(WAITING_MAX_HOURS).isBefore(LocalDateTime.now());
    }

    /**
     * Settles a request with nothing available: the shipment stays WAITING_FOR_MATCH
     * (still actively hunting) so the next recheck can find a truck for it.
     */
    private void settleEmpty(MatchRequest request) {
        request.setStatus(MatchRequest.Status.WAITING_FOR_MATCH);
        matchRequestRepository.save(request);
        courierServiceClient.updateShipmentStatus(request.getShipmentId(), "WAITING_FOR_MATCH", null);
    }

    /**
     * The single "a truck was matched" state transition. Used by EVERY flow —
     * initial request, rerun, waiting recheck, new-vehicle events, scheduler —
     * so a successful match always: flips the request WAITING_FOR_MATCH →
     * MATCH_FOUND exactly once, updates the shipment to MATCH_FOUND, and sends
     * EXACTLY ONE notification to the courier. Callers only invoke this after a
     * truck was genuinely reserved, and a request that reaches MATCH_FOUND
     * leaves the waiting pool — so it can never be transitioned or notified a
     * second time (TASKS 9, 12).
     *
     * <p>The shipment update and notification are best-effort inside this method:
     * if the courier-service or notification-service is down the match itself is
     * already saved as MATCH_FOUND and is never duplicated or lost — the fleet
     * reservation stands and the next recheck will not re-settle this request.
     */
    private void settleMatched(MatchRequest request, MatchResult assigned) {
        request.setStatus(MatchRequest.Status.MATCH_FOUND);
        matchRequestRepository.save(request);
        try {
            courierServiceClient.updateShipmentStatus(request.getShipmentId(), "MATCH_FOUND", null);
            notificationClient.notify(
                    request.getRequestedByUserId(),
                    "MATCH_FOUND",
                    "Your shipment is matched",
                    String.format("Truck %s (%s) has been matched and reserved for your shipment from %s to %s. " +
                                    "The fleet manager has been asked to confirm your booking.",
                            assigned.getTruckNo(), assigned.getFleetCompanyName(),
                            request.getPickupLocation(), request.getDestination()),
                    request.getId()
            );
        } catch (Exception e) {
            log.warn("Match for request {} saved, but shipment status/notification delivery failed: {}",
                    request.getId(), e.getMessage());
        }
    }

    /**
     * Permanent no-match (expiry): the wait window elapsed with no suitable truck,
     * so the request (and the shipment) stop looping and settle as NO_MATCH. The
     * courier is notified so the "waiting" state visibly concludes instead of
     * leaving them wondering.
     */
    private void expireWait(MatchRequest request) {
        request.setStatus(MatchRequest.Status.NO_MATCH);
        matchRequestRepository.save(request);
        courierServiceClient.updateShipmentStatus(request.getShipmentId(), "NO_MATCH", null);
        notificationClient.notify(
                request.getRequestedByUserId(),
                "NO_MATCH",
                "No truck found after waiting",
                String.format("No backhaul truck could be matched for %s to %s before the wait " +
                                "window closed. The shipment has been settled as no-match — try " +
                                "re-running the search, or refine the shipment's route/weight.",
                        request.getPickupLocation(), request.getDestination()),
                request.getId()
        );
    }

    /**
     * Cancel a waiting request whose shipment turned out to be invalid (cancelled,
     * already matched, bad data, etc.). Different from expiry: the shipment was
     * never a valid candidate, so we don't wait — we settle immediately.
     *
     * <p>Only touches the shipment status when the shipment is STILL in a hunting
     * state (PENDING/MATCHING/WAITING_FOR_MATCH). If it's already CANCELLED,
     * booked, in transit or otherwise advanced, that state is left alone — a
     * stale request is cancelled but never overwrites a more accurate shipment
     * status (TASK 13).
     */
    private void cancelInvalidWaiting(MatchRequest request, String shipmentStatus) {
        request.setStatus(MatchRequest.Status.CANCELLED);
        matchRequestRepository.save(request);
        if (shipmentStatus != null && ACTIVE_SHIPMENT_STATUSES.contains(shipmentStatus)) {
            courierServiceClient.updateShipmentStatus(request.getShipmentId(), "NO_MATCH", null);
        }
    }

    // ---- Reusable shipment validation ----

    private static final java.util.Set<String> ACTIVE_SHIPMENT_STATUSES = java.util.Set.of(
            "PENDING", "MATCHING", "WAITING_FOR_MATCH"
    );

    private static final java.util.Set<String> VALID_VEHICLE_TYPES = java.util.Set.of(
            "STANDARD", "REFRIGERATED", "FLATBED", "BOX_TRUCK", "TANKER"
    );

    /**
     * Validates that a shipment is eligible for backhaul matching. Called before
     * every matching attempt — initial request, rerun, and automatic recheck.
     *
     * <p>Collects all errors before failing so the caller sees every problem at
     * once. Throws {@link MatchingValidationException} on failure; the caller
     * decides whether to reject the HTTP request (initial flow) or skip the
     * shipment (recheck loop).
     */
    public void validateShipmentForMatching(Map<String, Object> shipment) {
        java.util.List<String> errors = new java.util.ArrayList<>();

        // ---- Shipment existence ----
        if (shipment == null || shipment.isEmpty()) {
            throw new MatchingValidationException(java.util.List.of("Shipment does not exist"));
        }

        // ---- Active / not cancelled / not already matched ----
        String status = ofString(shipment.get("status"));
        if (status == null) {
            errors.add("Shipment status is missing");
        } else if (status.equals("CANCELLED")) {
            errors.add("Shipment is cancelled");
        } else if (status.equals("COMPLETED") || status.equals("DELIVERED")
                || status.equals("IN_TRANSIT") || status.equals("DRIVER_ASSIGNED")) {
            errors.add("Shipment is already in transit or completed (status: " + status + ")");
        } else if (status.equals("CONFIRMED") || status.equals("BOOKING_PENDING")
                || status.equals("MATCHED")) {
            errors.add("Shipment is already matched or booked (status: " + status + ")");
        } else if (status.equals("NO_MATCH") || status.equals("REJECTED")) {
            errors.add("Shipment is in a terminal state (status: " + status + ")");
        } else if (!ACTIVE_SHIPMENT_STATUSES.contains(status)) {
            errors.add("Shipment is not in an active state for matching (status: " + status + ")");
        }

        // ---- Expiry: pickup datetime must be in the future ----
        LocalDateTime pickupDatetime = parseDateTime(ofString(shipment.get("pickupDatetime")));
        if (pickupDatetime == null) {
            errors.add("Shipment pickup date/time is missing or invalid");
        } else if (pickupDatetime.isBefore(LocalDateTime.now())) {
            errors.add("Shipment pickup date/time has expired");
        }

        // ---- Pickup and delivery locations ----
        String pickupLocation = ofString(shipment.get("pickupLocation"));
        String destination = ofString(shipment.get("destination"));
        if (pickupLocation == null || pickupLocation.isBlank()) {
            errors.add("Pickup location is required");
        }
        if (destination == null || destination.isBlank()) {
            errors.add("Delivery location is required");
        }
        if (pickupLocation != null && destination != null
                && pickupLocation.trim().equalsIgnoreCase(destination.trim())) {
            errors.add("Pickup and delivery locations must be different");
        }

        // ---- Pickup lat/lng ----
        Object pickupLat = shipment.get("pickupLatitude");
        Object pickupLng = shipment.get("pickupLongitude");
        if (pickupLat == null || pickupLng == null) {
            errors.add("Pickup latitude/longitude is required");
        }

        // ---- Delivery lat/lng ----
        Object destLat = shipment.get("destinationLatitude");
        Object destLng = shipment.get("destinationLongitude");
        if (destLat == null || destLng == null) {
            errors.add("Delivery latitude/longitude is required");
        }

        // ---- Weight ----
        Object weight = shipment.get("weightKg");
        if (weight == null) {
            errors.add("Shipment weight is required");
        } else {
            try {
                BigDecimal weightKg = new BigDecimal(weight.toString());
                if (weightKg.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add("Shipment weight must be greater than 0");
                }
            } catch (NumberFormatException e) {
                errors.add("Shipment weight is invalid");
            }
        }

        // ---- Vehicle type ----
        String vehicleType = ofString(shipment.get("requiredVehicleType"));
        if (vehicleType == null || vehicleType.isBlank()) {
            errors.add("Required vehicle type is required");
        } else if (!VALID_VEHICLE_TYPES.contains(vehicleType.trim().toUpperCase())) {
            errors.add("Invalid vehicle type: " + vehicleType
                    + ". Must be one of: STANDARD, REFRIGERATED, FLATBED, BOX_TRUCK, TANKER");
        }

        // ---- Delivery deadline (optional but must be valid when present) ----
        String deadlineStr = ofString(shipment.get("deliveryDeadline"));
        if (deadlineStr != null && !deadlineStr.isBlank()) {
            try {
                java.time.LocalDate deadline = java.time.LocalDate.parse(deadlineStr);
                if (pickupDatetime != null && deadline.isBefore(pickupDatetime.toLocalDate())) {
                    errors.add("Delivery deadline is before pickup date");
                }
            } catch (Exception e) {
                errors.add("Delivery deadline is invalid");
            }
        }

        if (!errors.isEmpty()) {
            throw new MatchingValidationException(errors);
        }
    }

    /** A candidate paired with its (already computed) MatchScorer evaluation. */
    private record ScoredCandidate(AvailabilityCandidate candidate, MatchScorer.Evaluation score) {}

    private MatchResult toScoredResult(MatchRequest request, ScoredCandidate sc) {
        AvailabilityCandidate c = sc.candidate();
        MatchScorer.Evaluation score = sc.score();

        MatchResult result = new MatchResult();
        result.setMatchRequestId(request.getId());
        result.setTruckAvailabilityId(c.id());
        result.setTruckId(c.truckId());
        result.setFleetCompanyId(c.fleetCompanyId());
        result.setFleetCompanyName(fleetServiceClient.getCompanyName(c.fleetCompanyId()));
        result.setTruckNo(c.truckNo());
        result.setAvailableCapacityTon(c.availableCapacityTon());
        result.setRouteFrom(c.routeFrom());
        result.setRouteTo(c.routeTo());
        result.setWeightKg(request.getWeightKg());
        result.setExpectedArrival(parseDateTime(c.expectedArrival()));
        result.setEstimatedCost(estimateCost(request, c));

        // distanceKm: the maximum of the pickup and delivery off-route distances
        // from the scored proximity (0–10 km for candidates that passed the
        // corridor). Falls back to endpoint-based deviation when the proximity
        // couldn't be measured at all.
        DistanceCalculator.RouteProximity proximity = score.proximity();
        double distanceKm;
        if (proximity != null) {
            distanceKm = Math.max(proximity.pickupDistanceKm(), proximity.deliveryDistanceKm());
        } else {
            distanceKm = distanceCalculator.routeDeviationKm(
                    request.getPickupLocation(), request.getDestination(), c.routeFrom(), c.routeTo());
        }
        result.setDistanceKm(distanceKm);

        // Match score comes straight from the centralized MatchScorer (0–100,
        // higher is better, components: route compat 40 / pickup prox 15 /
        // delivery prox 15 / capacity 15 / vehicle type 10 / date-time 5).
        result.setMatchScore(score.score());

        result.setStatus(MatchResult.Status.RECOMMENDED);
        return result;
    }

    /**
     * The Courier's "before you commit" price for this truck — delegates to
     * payment-service (the authoritative tariff, including the empty-return-leg
     * discount that's the whole point of backhaul). Falls back to the
     * quick estimate formula here only if payment-service is unreachable, so
     * the results screen never shows a blank price.
     */
    private BigDecimal estimateCost(MatchRequest request, AvailabilityCandidate candidate) {
        Double routeKm = distanceCalculator.distanceBetweenCities(request.getPickupLocation(), request.getDestination());
        BigDecimal estimate = paymentServiceClient.estimatePrice(
                routeKm, request.getWeightKg(), request.getRequiredVehicleType(), request.getPriority());
        if (estimate != null) {
            return estimate;
        }
        if (routeKm != null) {
            return baseFare.add(BigDecimal.valueOf(routeKm).multiply(ratePerKm)).setScale(2, RoundingMode.HALF_UP);
        }
        return candidate.availableCapacityTon().multiply(ratePerTonFallback).setScale(2, RoundingMode.HALF_UP);
    }

    public List<MatchResult> getResults(Long matchRequestId) {
        return matchResultRepository.findByMatchRequestIdOrderByMatchScoreDesc(matchRequestId);
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
     * Reserves a truck's capacity using the existing booking architecture: verifies
     * the slot one final time and atomically flips it to BOOKED in fleet-service,
     * records the CapacityReservation, moves the result to PENDING_CONFIRMATION and
     * notifies the fleet manager of the booking request. Throws a 409 Conflict when
     * the capacity was just taken by another shipment.
     */
    private MatchResult reserveTruck(MatchResult result, MatchRequest request) {
        BigDecimal requiredTon = toTons(request.getWeightKg());

        // Capacity Verification + reservation, atomically as far as fleet-service is concerned.
        fleetServiceClient.reserveCapacity(result.getTruckAvailabilityId(), requiredTon);

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

    /**
     * Auto-assignment for the initial Courier flow: walks the ranked candidates
     * highest-scoring first and reserves the first one whose capacity is still free
     * (atomic BOOKED in fleet-service). Candidates that were just scooped up by
     * another shipment in the race are marked REJECTED and the next-best is tried.
     * Returns the reserved result, or {@code null} if every candidate was taken.
     */
    private MatchResult assignBestMatch(MatchRequest request, List<MatchResult> results) {
        for (MatchResult candidate : results) {
            try {
                return reserveTruck(candidate, request);
            } catch (HttpClientErrorException.Conflict e) {
                candidate.setStatus(MatchResult.Status.REJECTED);
                matchResultRepository.save(candidate);
            }
        }
        return null;
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

        try {
            result = reserveTruck(result, request);
        } catch (HttpClientErrorException.Conflict e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This truck's capacity was just taken by another shipment — pick a different candidate");
        }

        courierServiceClient.updateShipmentStatus(request.getShipmentId(), "BOOKING_PENDING", null);

        return result;
    }

    /** "Booking Requests" page in the Fleet Portal: everything awaiting this company's decision. */
    public List<MatchResult> getPendingBookingsForUser(Long fleetManagerUserId) {
        Long companyId = fleetServiceClient.getCompanyIdForUser(fleetManagerUserId);
        return matchResultRepository.findByFleetCompanyIdAndStatusInOrderByCreatedAtDesc(
                companyId,
                List.of(
                        MatchResult.Status.RECOMMENDED,          // courier just requested backhaul transport
                        MatchResult.Status.PENDING_CONFIRMATION  // courier already accepted a specific truck
                ));
    }

    /** Fleet manager accepts — the reservation becomes permanent, siblings are auto-rejected. */
    public MatchResult acceptBooking(Long matchResultId) {
        MatchResult chosen = getResult(matchResultId);
        boolean courierAlreadyReserved = chosen.getStatus() == MatchResult.Status.PENDING_CONFIRMATION;
        if (!courierAlreadyReserved && chosen.getStatus() != MatchResult.Status.RECOMMENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking request is no longer pending");
        }

        // The courier only requested backhaul transport without accepting a specific truck
        // (RECOMMENDED) — the slot was never reserved, so reserve it now and record the
        // reservation before signing off on the booking.
        if (!courierAlreadyReserved) {
            MatchRequest pendingRequest = getRequest(chosen.getMatchRequestId());
            BigDecimal requiredTon = toTons(pendingRequest.getWeightKg());
            try {
                fleetServiceClient.reserveCapacity(chosen.getTruckAvailabilityId(), requiredTon);
            } catch (HttpClientErrorException.Conflict e) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "This truck's capacity was just taken by another shipment — pick a different candidate");
            }
            CapacityReservation reservation = new CapacityReservation();
            reservation.setMatchResultId(chosen.getId());
            reservation.setTruckAvailabilityId(chosen.getTruckAvailabilityId());
            reservation.setReservedCapacityTon(requiredTon);
            reservation.setStatus(CapacityReservation.Status.RESERVED);
            reservationRepository.save(reservation);
        }

        chosen.setStatus(MatchResult.Status.ACCEPTED);
        matchResultRepository.save(chosen);

        reservationRepository.findByMatchResultId(chosen.getId()).ifPresent(r -> {
            r.setStatus(CapacityReservation.Status.CONFIRMED);
            reservationRepository.save(r);
        });

        List<MatchResult> siblings = matchResultRepository.findByMatchRequestIdOrderByMatchScoreDesc(chosen.getMatchRequestId());
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

        // "Fleet receives booking" -> a real Trip (no driver yet — assigned separately),
        // and an invoice the courier can see cost/payment status for immediately.
        // Distance for the invoice is the shipment's actual route (OSRM driving
        // distance), NOT the truck's route deviation used for matching.
        Double invoiceDistance = distanceCalculator.distanceBetweenCities(
                request.getPickupLocation(), request.getDestination());
        fleetServiceClient.createTripForBooking(chosen.getTruckId(), request.getShipmentId());
        paymentServiceClient.createInvoice(
                request.getShipmentId(), chosen.getId(), request.getRequestedByUserId(), chosen.getFleetCompanyId(),
                chosen.getTruckNo(), invoiceDistance, request.getWeightKg(),
                request.getRequiredVehicleType(), request.getPriority()
        );

        courierServiceClient.updateShipmentStatus(request.getShipmentId(), "CONFIRMED", null);

        return chosen;
    }

    /** Fleet manager declines — release the reserved capacity, notify the courier, try again. */
    public MatchResult rejectBooking(Long matchResultId) {
        MatchResult result = getResult(matchResultId);
        if (result.getStatus() != MatchResult.Status.PENDING_CONFIRMATION
                && result.getStatus() != MatchResult.Status.RECOMMENDED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking request is no longer pending");
        }
        result.setStatus(MatchResult.Status.REJECTED);
        matchResultRepository.save(result);

        // Only release capacity that was actually reserved (courier had accepted a match first).
        reservationRepository.findByMatchResultId(result.getId()).ifPresent(r -> {
            if (r.getStatus() == CapacityReservation.Status.RESERVED) {
                fleetServiceClient.releaseCapacity(result.getTruckAvailabilityId());
                r.setStatus(CapacityReservation.Status.RELEASED);
                reservationRepository.save(r);
            }
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

        boolean anyLeft = matchResultRepository.findByMatchRequestIdOrderByMatchScoreDesc(request.getId()).stream()
                .anyMatch(r -> r.getStatus() == MatchResult.Status.RECOMMENDED || r.getStatus() == MatchResult.Status.PENDING_CONFIRMATION);
        if (!anyLeft) {
            // Every candidate was declined — put the shipment back on the hunt
            // (WAITING_FOR_MATCH) so new availability can re-match it automatically.
            settleEmpty(request);
        }

        return result;
    }

    private BigDecimal toTons(BigDecimal weightKg) {
        return weightKg == null ? BigDecimal.ZERO : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
    }

    private static java.time.LocalDateTime parseDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return java.time.LocalDateTime.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }

    /** Null-safe toString for a raw JSON map value, used when snapshotting the shipment. */
    private static String ofString(Object o) {
        return o == null ? null : o.toString();
    }
}
