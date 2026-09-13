package com.backhaulmatch.matching.service;

import com.backhaulmatch.matching.dto.MatchingDtos.AvailabilityCandidate;
import com.backhaulmatch.matching.entity.MatchRequest;
import com.backhaulmatch.matching.util.DistanceCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * THE one service-level method for deciding whether a vehicle fits a shipment.
 *
 * <p>Every matching flow — the courier's initial match request, a manual rerun,
 * the waiting-shipment recheck, new-vehicle events pushed from fleet-service, and
 * the scheduled retry — evaluates each candidate through this exact same class.
 * There is deliberately NO other matching logic anywhere: something is
 * {@link Evaluation#valid() valid} iff this method says so.
 *
 * <p><b>Scoring model</b> (0–100, higher is better) — weights are shared across
 * all entry points (initial request, rerun, waiting recheck, new-vehicle push,
 * scheduled retry):
 * <pre>
 *   Route compatibility  60   corridor gate — pickup &amp; delivery both within 10 km
 *                              of the truck's OSRM road route AND pickup before
 *                              delivery along that route (10 pts), plus how
 *                              closely the pickup/delivery sit ON the route
 *                              (0–2 km: 25 · >2–5 km: 20 · >5–10 km: 13; >10 km: reject)
 *   Capacity             20   how exactly the truck's spare capacity fits the shipment
 *   Vehicle type         10   10 if the requested type matches, 8 if unspecified
 *   Date/time            10   10 if the pickup falls inside the availability window
 *   ─────────────────────────
 *   Maximum              100
 * </pre>
 *
 * <p><b>Route direction/containment:</b> the corridor logic guarantees the truck
 * travels in the same direction as the shipment — the pickup must be reached
 * along the truck's OSRM road route BEFORE the delivery. So a truck whose route
 * contains/overlaps the shipment's route matches (exact route scores highest,
 * partial overlap lower) while a truck heading the opposite way is rejected.
 *
 * <p><b>Hard requirements</b> — any candidate that fails one of these is rejected
 * with a specific {@link Evaluation#reason()}:
 * <ul>
 *   <li>shipment snapshot is valid</li>
 *   <li>vehicle availability is valid</li>
 *   <li>sufficient capacity</li>
 *   <li>compatible vehicle type</li>
 *   <li>overlapping availability date/time</li>
 *   <li>pickup within route corridor (≤ 10 km)</li>
 *   <li>delivery within route corridor (≤ 10 km)</li>
 *   <li>pickup before delivery on the route</li>
 *   <li>score ≥ {@link #MIN_MATCH_SCORE}</li>
 * </ul>
 *
 * <p><b>Resilience:</b> the corridor checks rely on OSRM geometry. When OSRM is
 * unreachable (fallback haversine distances) or a location can't be resolved,
 * the corridor is treated leniently — a routing outage never silently suppresses
 * all matches — but proximity still scores from whatever distances are available
 * so ranking degrades gracefully instead of breaking.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MatchScorer {

    public static final double MIN_MATCH_SCORE = 60.0;

    /** Corridor limit: pickup/delivery more than this far from the road route is rejected. */
    public static final double MAX_ROUTE_DEVIATION_KM = 10.0;

    // Component weights (sum to 100): route compatibility 60, capacity 20,
    // vehicle type 10, date/time 10.
    private static final double ROUTE_COMPAT_PTS = 10.0;   // corridor + direction gate passed
    private static final double PICKUP_PROX_PTS = 25.0;    // pickup closely ON the OSRM route
    private static final double DELIVERY_PROX_PTS = 25.0;  // delivery closely ON the OSRM route
    private static final double CAPACITY_PTS = 20.0;
    private static final double VEHICLE_TYPE_PTS = 10.0;
    private static final double DATE_TIME_PTS = 10.0;

    // Proximity bands (km → points), applied to pickup AND delivery (both feed
    // the 60 route-compatibility buckets).
    private static final double BAND_EXCELLENT_KM = 2.0;   // 0–2 km   → 25 points
    private static final double BAND_GOOD_KM = 5.0;        // >2–5 km  → 20 points
    private static final double BAND_ACCEPTABLE_KM = 10.0; // >5–10 km → 13 points
    // >10 km → reject (corridor hard requirement)

    // Date/time overlap tolerance: the backhaul truck can still take the shipment
    // if its pickup falls near the availability window. This many hours of slack
    // on either edge keeps the check usable for near-miss times.
    private static final long DATE_TOLERANCE_HOURS = 24;

    private final DistanceCalculator distanceCalculator;

    /**
     * Evaluates ONE (shipment, vehicle) pair end-to-end and returns the complete
     * result: whether the match is valid, its 0–100 score, and — when rejected —
     * exactly why. This is the ONLY method that decides a match; every entry point
     * (initial, rerun, waiting recheck, new-vehicle events, scheduled retries)
     * calls this same method.
     *
     * <p>Sequence: validate shipment snapshot → validate vehicle availability →
     * capacity → vehicle type → date/time → OSRM route geometry → pickup
     * proximity → delivery proximity → pickup-before-delivery order → score →
     * minimum-score check → return {@link Evaluation}.
     */
    public Evaluation evaluateVehicleForShipment(MatchRequest shipment, AvailabilityCandidate vehicle) {
        // ---- 1. Validate shipment ----
        String shipmentIssue = validateShipmentSnapshot(shipment);
        if (shipmentIssue != null) {
            return reject(null, "shipment: " + shipmentIssue);
        }

        // ---- 2. Validate vehicle ----
        String vehicleIssue = validateVehicle(vehicle);
        if (vehicleIssue != null) {
            return reject(null, "vehicle: " + vehicleIssue);
        }

        BigDecimal requiredTon = toTons(shipment.getWeightKg());

        // ---- 3. Check capacity (hard requirement) ----
        if (vehicle.availableCapacityTon().compareTo(requiredTon) < 0) {
            return reject(null, "insufficient capacity: needs " + requiredTon.stripTrailingZeros().toPlainString()
                    + " t, truck offers " + vehicle.availableCapacityTon().stripTrailingZeros().toPlainString() + " t");
        }

        // ---- 4. Check vehicle type (hard requirement) ----
        if (!vehicleTypeCompatible(shipment.getRequiredVehicleType(), vehicle.truckType())) {
            return reject(null, "vehicle type mismatch: requested "
                    + shipment.getRequiredVehicleType() + ", truck is " + vehicle.truckType());
        }

        // ---- 5. Check date/time overlap (hard requirement) ----
        if (!dateTimeOverlaps(shipment, vehicle)) {
            return reject(null, "availability window does not overlap the pickup time");
        }

        // ---- 6. Get the OSRM route geometry ----
        DistanceCalculator.RouteProximity proximity = distanceCalculator.computeRouteProximity(
                shipment.getPickupLocation(), shipment.getDestination(),
                vehicle.routeFrom(), vehicle.routeTo());

        double routeCompatPts;
        double pickupPts;
        double deliveryPts;
        if (proximity == null) {
            // Locations can't be resolved — corridor unmeasurable. Lenient pass,
            // but no proximity points (nothing reliable to score).
            log.warn("Route proximity unavailable for shipment {} ({} -> {}) on vehicle {} — " +
                            "treating the corridor as unmeasured, not as a no-match.",
                    shipment.getId(), shipment.getPickupLocation(), shipment.getDestination(), vehicle.truckNo());
            routeCompatPts = ROUTE_COMPAT_PTS;
            pickupPts = 0;
            deliveryPts = 0;
        } else if (!proximity.osrmRouted()) {
            // OSRM unreachable — fallback distances can't prove a point is really
            // on the road, so the corridor is passed leniently (no false rejects)
            // and the proximity bands score the fallback for graceful ranking.
            log.warn("OSRM routing unavailable for shipment {} ({} -> {}) on vehicle {} — " +
                            "using straight-line fallback; shipment stays eligible and will be re-checked.",
                    shipment.getId(), shipment.getPickupLocation(), shipment.getDestination(), vehicle.truckNo());
            routeCompatPts = ROUTE_COMPAT_PTS;
            pickupPts = bandPoints(proximity.pickupDistanceKm());
            deliveryPts = bandPoints(proximity.deliveryDistanceKm());
        } else {
            // Genuine OSRM geometry — enforce the corridor hard requirements.

            // ---- 7. Pickup proximity (corridor + points) ----
            if (proximity.pickupDistanceKm() > MAX_ROUTE_DEVIATION_KM) {
                return reject(proximity, "pickup is " + round(proximity.pickupDistanceKm())
                        + " km off the route corridor (limit " + MAX_ROUTE_DEVIATION_KM + " km)");
            }

            // ---- 8. Delivery proximity (corridor + points) ----
            if (proximity.deliveryDistanceKm() > MAX_ROUTE_DEVIATION_KM) {
                return reject(proximity, "delivery is " + round(proximity.deliveryDistanceKm())
                        + " km off the route corridor (limit " + MAX_ROUTE_DEVIATION_KM + " km)");
            }

            // ---- 9. Pickup-before-delivery order (hard requirement) ----
            if (!proximity.pickupBeforeDelivery()) {
                return reject(proximity, "pickup does not occur before delivery on the truck's route");
            }

            routeCompatPts = ROUTE_COMPAT_PTS;
            pickupPts = bandPoints(proximity.pickupDistanceKm());
            deliveryPts = bandPoints(proximity.deliveryDistanceKm());
        }

        // ---- 10. Calculate the match score (0–100) ----
        // Capacity fit: full 20 when the truck's spare capacity matches the
        // shipment exactly, decaying as spare capacity grows.
        double capacityPts = CAPACITY_PTS
                * requiredTon.doubleValue()
                / Math.max(vehicle.availableCapacityTon().doubleValue(), requiredTon.doubleValue());

        // Vehicle type: 10 for an explicit requested-type match, 8 when no type
        // was requested (any compatible type assumed).
        double vehicleTypePts = (shipment.getRequiredVehicleType() != null
                && !shipment.getRequiredVehicleType().isBlank())
                ? VEHICLE_TYPE_PTS : 8.0;

        // Date/time: always 10 — non-overlapping windows were already rejected
        // as a hard requirement, so every survivor is inside.
        double dateTimePts = DATE_TIME_PTS;

        double total = routeCompatPts + pickupPts + deliveryPts + capacityPts + vehicleTypePts + dateTimePts;

        // ---- 11. Check the minimum score ----
        if (total < MIN_MATCH_SCORE) {
            return reject(proximity, "score " + round(total) + " below the minimum " + MIN_MATCH_SCORE);
        }

        // ---- 12. Valid match ----
        return new Evaluation(true, null, round(total), proximity,
                routeCompatPts, pickupPts, deliveryPts, capacityPts, vehicleTypePts, dateTimePts);
    }

    /**
     * Step 1: validates the shipment snapshot used for matching. The full
     * shipment validation against live courier-service state (cancelled, already
     * matched, pickup expired, ...) is a flow-level concern performed once per
     * request before candidates are evaluated — this guard verifies the snapshot
     * itself is usable so a malformed request can never slip through.
     */
    private String validateShipmentSnapshot(MatchRequest shipment) {
        if (shipment == null) return "shipment is missing";
        String pickup = shipment.getPickupLocation();
        String destination = shipment.getDestination();
        if (pickup == null || pickup.isBlank()) return "pickup location is required";
        if (destination == null || destination.isBlank()) return "delivery location is required";
        if (pickup.trim().equalsIgnoreCase(destination.trim())) return "pickup and delivery must be different";
        BigDecimal weight = shipment.getWeightKg();
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) return "shipment weight must be greater than 0";
        return null;
    }

    /**
     * Step 2: validates a single vehicle availability row returned by
     * fleet-service. Guards against race conditions (a truck booked between the
     * search and the scoring pass) and malformed data. Returns a reason string,
     * or {@code null} when the vehicle is valid.
     */
    private String validateVehicle(AvailabilityCandidate v) {
        if (v == null) return "vehicle is missing";
        if (v.id() == null || v.truckId() == null) return "vehicle references are incomplete";
        if (v.truckStatus() != null && !"AVAILABLE".equals(v.truckStatus())) {
            return "truck is not available (status: " + v.truckStatus() + ")";
        }
        if (v.availabilityStatus() != null && !"AVAILABLE".equals(v.availabilityStatus())) {
            return "availability slot is not open (status: " + v.availabilityStatus() + ")";
        }
        if (v.routeFrom() == null || v.routeFrom().isBlank()
                || v.routeTo() == null || v.routeTo().isBlank()) {
            return "vehicle route is incomplete";
        }
        if (v.truckNo() == null || v.truckNo().isBlank()) return "truck number is missing";
        if (v.availableCapacityTon() == null || v.availableCapacityTon().compareTo(BigDecimal.ZERO) <= 0) {
            return "available capacity is not positive";
        }
        LocalDateTime availableFrom = parseDateTime(v.availableFrom());
        if (availableFrom == null) return "availability start date/time is missing or invalid";
        if (availableFrom.isBefore(LocalDateTime.now())) return "availability window has already started/expired";
        return null;
    }

    private Evaluation reject(DistanceCalculator.RouteProximity proximity, String reason) {
        return new Evaluation(false, reason, 0.0, proximity, 0, 0, 0, 0, 0, 0);
    }

    /** Proximity band ladder: 0–2 → 25, 2–5 → 20, 5–10 → 13, >10 → 0 (reject is a hard requirement upstream). */
    private double bandPoints(double distanceKm) {
        if (distanceKm <= BAND_EXCELLENT_KM) return PICKUP_PROX_PTS;
        if (distanceKm <= BAND_GOOD_KM) return 20.0;
        if (distanceKm <= BAND_ACCEPTABLE_KM) return 13.0;
        return 0.0;
    }

    /**
     * Vehicle-type compatibility: if the courier stated a required truck type, a
     * vehicle of a different type isn't a fit — refrigerated cargo can't go on an
     * open flatbed, for instance. A null/unspecified requirement (or a truck with
     * no type recorded) is treated as compatible.
     */
    private boolean vehicleTypeCompatible(String requiredType, String truckType) {
        if (requiredType == null || requiredType.isBlank()) return true;
        if (truckType == null || truckType.isBlank()) return true;
        // Normalize: strip underscores, collapse whitespace, lowercase — so
        // "BOX_TRUCK" (enum form on shipments) matches "Box Truck" (on the truck).
        String normRequired = requiredType.trim().toUpperCase().replaceAll("[_\\s]+", "");
        String normTruck = truckType.trim().toUpperCase().replaceAll("[_\\s]+", "");
        return normRequired.equals(normTruck);
    }

    /**
     * Date/time compatibility: the backhaul truck can only carry the shipment if
     * the shipment's pickup falls inside the window the truck is available — after
     * it departs (availableFrom) and before it reaches its destination
     * (expectedArrival, when it must turn around). A small tolerance on the back
     * edge lets a truck that posts availability slightly late still match. A
     * missing pickup or availability date is treated as "no constraint".
     */
    private boolean dateTimeOverlaps(MatchRequest shipment, AvailabilityCandidate vehicle) {
        LocalDateTime pickup = shipment.getPickupDatetime();
        if (pickup == null) return true;
        LocalDateTime from = parseDateTime(vehicle.availableFrom());
        LocalDateTime to = parseDateTime(vehicle.expectedArrival());
        if (from != null && pickup.isBefore(from.minusHours(DATE_TOLERANCE_HOURS))) return false;
        if (to != null && pickup.isAfter(to.plusHours(DATE_TOLERANCE_HOURS))) return false;
        return true;
    }

    /**
     * The complete result of evaluating one vehicle for one shipment: {@code true}
     * when the vehicle is a valid match (every hard requirement passed AND the
     * score reached {@link #MIN_MATCH_SCORE}), the 0–100 score, why it was
     * rejected (null when valid), and the component points for transparency/debug.
     */
    public record Evaluation(
            boolean valid,
            String reason,              // null when valid
            double score,               // 0–100 (0 when the vehicle was rejected)
            DistanceCalculator.RouteProximity proximity,
            double routeCompatPts,
            double pickupProxPts,
            double deliveryProxPts,
            double capacityPts,
            double vehicleTypePts,
            double dateTimePts
    ) {}

    private static BigDecimal toTons(BigDecimal weightKg) {
        return weightKg == null ? BigDecimal.ZERO : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static LocalDateTime parseDateTime(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception e) {
            return null;
        }
    }
}