package com.backhaulmatch.matching.util;

import com.backhaulmatch.matching.client.RoutingServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves place-name locations to coordinates and computes the distance
 * between them.
 *
 * Distance: OSRM (real road routing) is the primary source of truth. The fixed
 * city table below is NOT used for distances anymore — it survives only as a
 * lightweight geocoder fallback, because OSRM itself works purely in
 * coordinates and can't turn "Kandy" into (7.29, 80.63). Swap it for a real
 * geocoding service later if shipment/availability addresses go beyond cities.
 *
 * Resilience: if OSRM is unreachable (e.g. its container was just restarted)
 * the engine degrades gracefully to a great-circle (haversine) distance between
 * the same coordinates rather than failing the match.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistanceCalculator {

    private final RoutingServiceClient routingServiceClient;

    public static final double EARTH_RADIUS_KM = 6371.0;

    // lat, lng for each city's town center — GEOCODER ONLY (name -> coordinates).
    // No distances are computed from this table; those come from OSRM.
    private static final Map<String, double[]> CITY_COORDINATES = Map.ofEntries(
            Map.entry("colombo", new double[]{6.9271, 79.8612}),
            Map.entry("kandy", new double[]{7.2906, 80.6337}),
            Map.entry("galle", new double[]{6.0535, 80.2210}),
            Map.entry("kurunegala", new double[]{7.4863, 80.3647}),
            Map.entry("anuradhapura", new double[]{8.3114, 80.4037}),
            Map.entry("negombo", new double[]{7.2083, 79.8358}),
            Map.entry("jaffna", new double[]{9.6615, 80.0255}),
            Map.entry("matara", new double[]{5.9549, 80.5550}),
            Map.entry("trincomalee", new double[]{8.5874, 81.2152}),
            Map.entry("batticaloa", new double[]{7.7170, 81.7000}),
            Map.entry("ratnapura", new double[]{6.6828, 80.4012}),
            Map.entry("badulla", new double[]{6.9934, 81.0550}),
            Map.entry("nuwara eliya", new double[]{6.9497, 80.7891}),
            Map.entry("polonnaruwa", new double[]{7.9403, 81.0188}),
            Map.entry("ampara", new double[]{7.2975, 81.6747}),
            Map.entry("puttalam", new double[]{8.0362, 79.8283}),
            Map.entry("vavuniya", new double[]{8.7514, 80.4971}),
            Map.entry("hambantota", new double[]{6.1246, 81.1185}),
            Map.entry("gampaha", new double[]{7.0917, 80.0000}),
            Map.entry("kalutara", new double[]{6.5854, 79.9607}),
            Map.entry("kegalle", new double[]{7.2506, 80.3447}),
            Map.entry("avissawella", new double[]{6.9530, 80.2183}),
            Map.entry("gampola", new double[]{7.1667, 80.5667}),
            Map.entry("nawalapitiya", new double[]{7.0457, 80.5370}),
            Map.entry("peradeniya", new double[]{7.2631, 80.5967}),
            Map.entry("chilaw", new double[]{7.5755, 79.7952}),
            Map.entry("nittambuwa", new double[]{7.1454, 80.0655}),
            Map.entry("homagama", new double[]{6.8440, 80.0020}),
            Map.entry("dehiwala", new double[]{6.8452, 79.8744}),
            Map.entry("panadura", new double[]{6.7137, 79.9120}),
            Map.entry("moratuwa", new double[]{6.7730, 79.8812}),
            Map.entry("horana", new double[]{6.7160, 80.0590}),
            Map.entry("dambulla", new double[]{7.8600, 80.6510}),
            Map.entry("matale", new double[]{7.4675, 80.6234}),
            Map.entry("hatton", new double[]{6.8974, 80.5982})
    );

    // Distance assumed when a location can't be resolved to coordinates — large
    // enough to sort behind any real match, but not infinite (keeps the
    // scoring/filtering stable).
    private static final double UNKNOWN_LOCATION_PENALTY_KM = 500.0;

    /**
     * Result of checking how closely a shipment's pickup and delivery match a
     * vehicle's route. Both distances are from OSRM road routing when available
     * (great-circle fallback). {@code pickupBeforeDelivery} is {@code true}
     * only when the pickup point is reached before the delivery point along the
     * vehicle's route. {@code osrmRouted} is {@code false} when the distances
     * are only a great-circle fallback (OSRM unreachable / route empty) — the
     * caller must NOT apply a hard corridor threshold to fallback data, since
     * endpoints alone can't tell whether a point is really on the road route.
     */
    public record RouteProximity(
            double pickupDistanceKm,
            double deliveryDistanceKm,
            boolean pickupBeforeDelivery,
            boolean osrmRouted
    ) {}

    /**
     * Driving (OSRM) distance in km between two named locations, with a
     * great-circle fallback whenever OSRM can't be reached. Returns null only
     * when one of the locations can't be resolved to coordinates at all — the
     * caller decides (cost falls back to a flat rate, matching treats it as a
     * 500 km penalty).
     */
    public Double distanceBetweenCities(String cityA, String cityB) {
        double[] a = resolve(cityA);
        double[] b = resolve(cityB);
        if (a == null || b == null) return null;

        Double routed = routingServiceClient.routeKm(a[0], a[1], b[0], b[1]);
        if (routed != null && routed > 0) return routed;
        return haversineKm(a[0], a[1], b[0], b[1]);
    }

    /**
     * Total "detour" a candidate truck represents for a shipment: how far its
     * posted start is from the shipment's actual pickup, plus how far its
     * posted end is from the shipment's actual destination. Zero means a
     * perfect route match. Each leg is an OSRM driving distance; an
     * unresolvable leg adds a fixed penalty so it gets deprioritized instead
     * of breaking matching.
     */
    public double routeDeviationKm(String shipmentPickup, String shipmentDestination,
                                    String candidateFrom, String candidateTo) {
        Double pickupLeg = distanceBetweenCities(shipmentPickup, candidateFrom);
        Double destinationLeg = distanceBetweenCities(shipmentDestination, candidateTo);
        return (pickupLeg == null ? UNKNOWN_LOCATION_PENALTY_KM : pickupLeg)
                + (destinationLeg == null ? UNKNOWN_LOCATION_PENALTY_KM : destinationLeg);
    }

    /**
     * Computes how closely a shipment's pickup and delivery match a vehicle's
     * planned route. The vehicle's route is fetched from OSRM as a full
     * polyline geometry (road-routed), then each shipment point is measured
     * against every segment of that polyline to find the minimum distance.
     *
     * <p>Returns a {@link RouteProximity} with the two distances and whether
     * the pickup occurs before the delivery along the route. Returns
     * {@code null} only when a location can't be resolved to coordinates at all;
     * when OSRM is unreachable it instead returns a {@code RouteProximity} with
     * great-circle distances and {@code osrmRouted=false} so the caller can
     * degrade gracefully.
     */
    public RouteProximity computeRouteProximity(String shipmentPickup, String shipmentDelivery,
                                                 String vehicleRouteFrom, String vehicleRouteTo) {
        double[] pickup = resolve(shipmentPickup);
        double[] delivery = resolve(shipmentDelivery);
        double[] routeStart = resolve(vehicleRouteFrom);
        double[] routeEnd = resolve(vehicleRouteTo);
        if (pickup == null || delivery == null || routeStart == null || routeEnd == null) {
            return null;
        }

        double[][] waypoints = {routeStart, routeEnd};
        Object[] osrmResult = routingServiceClient.routeWithGeometry(waypoints);

        if (osrmResult == null || osrmResult.length < 2) {
            // OSRM unavailable — degrade to endpoint-to-point haversine
            return new RouteProximity(
                    haversineKm(pickup[0], pickup[1], routeStart[0], routeStart[1]),
                    haversineKm(delivery[0], delivery[1], routeEnd[0], routeEnd[1]),
                    true,
                    false
            );
        }

        String encodedPolyline = (String) osrmResult[1];
        List<double[]> routeCoords = decodePolyline(encodedPolyline);
        if (routeCoords.isEmpty()) {
            return new RouteProximity(
                    haversineKm(pickup[0], pickup[1], routeStart[0], routeStart[1]),
                    haversineKm(delivery[0], delivery[1], routeEnd[0], routeEnd[1]),
                    true,
                    false
            );
        }

        double pickupMinDist = minDistanceToRoute(pickup[0], pickup[1], routeCoords);
        double deliveryMinDist = minDistanceToRoute(delivery[0], delivery[1], routeCoords);

        int pickupIdx = nearestSegmentIndex(pickup[0], pickup[1], routeCoords);
        int deliveryIdx = nearestSegmentIndex(delivery[0], delivery[1], routeCoords);

        return new RouteProximity(pickupMinDist, deliveryMinDist, pickupIdx <= deliveryIdx, true);
    }

    // ---- Coordinate resolution ----

    /**
     * Resolve a location name or coordinate string to {lat, lng}. Public so
     * callers (e.g. {@code MatchingService}) can use the same geocoder.
     * Returns {@code null} when the location can't be resolved.
     */
    public double[] resolve(String location) {
        if (location == null) return null;
        String trimmed = location.trim();
        if (trimmed.isEmpty()) return null;

        double[] literal = parseLatLng(trimmed);
        if (literal != null) return literal;

        double[] coords = CITY_COORDINATES.get(trimmed.toLowerCase());
        if (coords != null) return coords;

        String[] parts = trimmed.split(",");
        for (String part : parts) {
            coords = CITY_COORDINATES.get(part.trim().toLowerCase());
            if (coords != null) return coords;
        }
        return null;
    }

    /** Parses an explicit "lat,lng" string (e.g. "6.9271,79.8612"); null if it isn't a coordinate pair. */
    public static double[] parseLatLng(String value) {
        String[] parts = value.split(",");
        if (parts.length != 2) return null;
        try {
            double lat = Double.parseDouble(parts[0].trim());
            double lng = Double.parseDouble(parts[1].trim());
            if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) return new double[]{lat, lng};
        } catch (NumberFormatException ignored) {
            // not a coordinate pair
        }
        return null;
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ---- Local geocoder (search + reverse), same city table as resolve() ----

    /**
     * A location suggestion: one of the known Sri Lankan cities plus its
     * town-center coordinates. No external services are involved — OSRM does
     * all the routing, this only turns a place name (or literal lat,lng) into
     * coordinates the portals can store on the shipment/availability.
     */
    public record GeoLocation(String name, double latitude, double longitude, double distanceKm) {}

    /**
     * Search for the portal's location autocomplete. Returns an explicit
     * "lat,lng" literal entry first if the query parses as coordinates, then
     * the known cities whose name contains the query (case-insensitive).
     */
    public List<GeoLocation> searchLocations(String query, int limit) {
        List<GeoLocation> results = new ArrayList<>();
        if (query == null || query.isBlank()) return results;
        String q = query.trim();
        int max = Math.max(1, limit);

        double[] literal = parseLatLng(q);
        if (literal != null) {
            results.add(new GeoLocation(q, literal[0], literal[1], 0));
            if (results.size() >= max) return results;
        }

        String norm = q.toLowerCase();
        for (Map.Entry<String, double[]> e : CITY_COORDINATES.entrySet()) {
            if (results.size() >= max) break;
            String name = e.getKey();
            if (name.contains(norm)) {
                results.add(new GeoLocation(capitalize(name), e.getValue()[0], e.getValue()[1], 0));
            }
        }
        return results;
    }

    /**
     * Nearest known city to a coordinate — the side the portals use when a
     * user clicks a point on the map. Returns the literal coordinates as the
     * name when nothing is close.
     */
    public GeoLocation reverseGeocode(double lat, double lng) {
        String bestName = null;
        double bestDist = Double.MAX_VALUE;
        double bestLat = lat;
        double bestLng = lng;
        for (Map.Entry<String, double[]> e : CITY_COORDINATES.entrySet()) {
            double d = haversineKm(lat, lng, e.getValue()[0], e.getValue()[1]);
            if (d < bestDist) {
                bestDist = d;
                bestName = e.getKey();
                bestLat = e.getValue()[0];
                bestLng = e.getValue()[1];
            }
        }
        if (bestName == null) {
            return new GeoLocation(String.format("%.5f, %.5f", lat, lng), lat, lng, 0);
        }
        return new GeoLocation(capitalize(bestName), bestLat, bestLng, bestDist);
    }

    private static String capitalize(String name) {
        if (name.isEmpty()) return name;
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    // ---- Polyline decoding (Google Encoded Polyline Algorithm) ----

    /**
     * Decodes a Google-encoded polyline string into a list of {lat, lng}
     * coordinate pairs. Used to interpret OSRM's {@code geometry} field when
     * {@code geometries=polyline} is requested.
     *
     * @see <a href="https://developers.google.com/maps/documentation/utilities/polylinealgorithm">
     *      Google Encoded Polyline Algorithm</a>
     */
    public static List<double[]> decodePolyline(String encoded) {
        List<double[]> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) return points;

        int index = 0;
        int lat = 0;
        int lng = 0;

        while (index < encoded.length()) {
            int shift = 0;
            int result = 0;
            byte b;
            do {
                b = (byte) (encoded.charAt(index++) - 63);
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lat += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            shift = 0;
            result = 0;
            do {
                b = (byte) (encoded.charAt(index++) - 63);
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            lng += ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));

            points.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return points;
    }

    // ---- Route proximity helpers ----

    /**
     * Minimum haversine distance (km) from a point to any segment of the
     * decoded route polyline.
     */
    private double minDistanceToRoute(double lat, double lng, List<double[]> route) {
        double min = Double.MAX_VALUE;
        for (int i = 0; i < route.size() - 1; i++) {
            double dist = pointToSegmentHaversine(
                    lat, lng,
                    route.get(i)[0], route.get(i)[1],
                    route.get(i + 1)[0], route.get(i + 1)[1]);
            if (dist < min) min = dist;
        }
        return min;
    }

    /**
     * Index of the nearest route segment to a point — used to determine
     * whether pickup occurs before (or at the same position as) delivery
     * along the vehicle's route.
     */
    private int nearestSegmentIndex(double lat, double lng, List<double[]> route) {
        double min = Double.MAX_VALUE;
        int bestIdx = 0;
        for (int i = 0; i < route.size() - 1; i++) {
            double dist = pointToSegmentHaversine(
                    lat, lng,
                    route.get(i)[0], route.get(i)[1],
                    route.get(i + 1)[0], route.get(i + 1)[1]);
            if (dist < min) {
                min = dist;
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /**
     * Minimum haversine distance from a point to a line segment (not the
     * infinite line). Projects the point onto the segment; if the projection
     * falls outside the segment, returns the distance to the nearest endpoint.
     */
    private double pointToSegmentHaversine(
            double px, double py,
            double ax, double ay,
            double bx, double by) {
        // Haversine doesn't support addition/subtraction, so project in
        // approximate Cartesian (good enough at city-scale distances).
        double dx = (bx - ax);
        double dy = (by - ay);
        double lenSq = dx * dx + dy * dy;
        if (lenSq == 0) return haversineKm(px, py, ax, ay);

        double t = Math.max(0, Math.min(1,
                ((px - ax) * dx + (py - ay) * dy) / lenSq));
        double projLat = ax + t * dx;
        double projLng = ay + t * dy;
        return haversineKm(px, py, projLat, projLng);
    }
}
