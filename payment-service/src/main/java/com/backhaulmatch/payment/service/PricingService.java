package com.backhaulmatch.payment.service;

import com.backhaulmatch.payment.client.AdminSettingsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * The authoritative cost calculation for a booking — separate from the quick
 * estimate matching-service shows at match time, which exists only so the
 * courier has a number to look at before committing. This is what actually
 * gets invoiced, and it's the one place pricing logic lives.
 *
 * All tariff inputs are read from admin-service's System Settings (keys
 * prefixed "pricing.") so the admin can change rates from the Admin Portal
 * without a redeploy. If admin-service is unreachable, compiled-in defaults
 * are used so pricing never breaks.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PricingService {

    private final AdminSettingsClient adminSettingsClient;

    // Fallback tariff, used only while admin-service settings aren't reachable.
    private static final BigDecimal DEFAULT_BASE_FARE = new BigDecimal("1000");   // LKR, flat
    private static final BigDecimal DEFAULT_RATE_PER_KM = new BigDecimal("150");  // LKR per km
    private static final BigDecimal DEFAULT_RATE_PER_TON = new BigDecimal("300"); // LKR per ton, on top of distance
    private static final double DEFAULT_DISTANCE_KM = 150.0; // used if distanceKm wasn't supplied

    private Map<String, String> settings;

    private Map<String, String> getSettings() {
        if (settings == null) {
            settings = adminSettingsClient.getSettings();
        }
        return settings;
    }

    private BigDecimal decimalSetting(Map<String, String> s, String key, BigDecimal fallback) {
        try {
            return new BigDecimal(s.getOrDefault(key, fallback.toString()));
        } catch (Exception e) {
            return fallback;
        }
    }

    private double doubleSetting(Map<String, String> s, String key, double fallback) {
        try {
            return Double.parseDouble(s.getOrDefault(key, String.valueOf(fallback)));
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Parses a "STANDARD:1.0,REFRIGERATED:1.3" string into a map (missing key -> 1.0). */
    private double multiplierFor(Map<String, String> s, String key, String item) {
        String raw = s.getOrDefault(key, "");
        try {
            for (String entry : raw.split(",")) {
                String[] kv = entry.split(":");
                if (kv.length == 2 && kv[0].trim().equalsIgnoreCase(item)) {
                    return Double.parseDouble(kv[1].trim());
                }
            }
        } catch (Exception e) {
            // fall through to default
        }
        return 1.0;
    }

    private BigDecimal backhaulDiscountPct(Map<String, String> s) {
        return decimalSetting(s, "pricing.backhaulDiscountPct", new BigDecimal("10"));
    }

    /**
     * Behaves like a per-request lookup so a settings change is picked up on
     * the next call once someone re-requests (a simple cache instead of
     * hitting admin-service every single time).
     */
    public void invalidateCache() {
        settings = null;
    }

    /**
     * Cost Calculation: (base fare + distance charge + weight charge)
     *   x vehicle-type multiplier x priority multiplier
     *   x (1 - backhaul discount %).
     *
     * distanceKm and weightKg are both optional inputs (a caller might not
     * have one or the other) — missing values fall back to sane defaults
     * rather than producing a zero or null invoice amount.
     */
    public BigDecimal calculateCost(Double distanceKm, BigDecimal weightKg) {
        return calculateCost(distanceKm, weightKg, null, null, false);
    }

    public BigDecimal calculateCost(Double distanceKm, BigDecimal weightKg,
                                    String vehicleType, String priority, boolean backhaul) {
        Map<String, String> s = getSettings();
        BigDecimal baseFare = decimalSetting(s, "pricing.baseFare", DEFAULT_BASE_FARE);
        BigDecimal ratePerKm = decimalSetting(s, "pricing.ratePerKm", DEFAULT_RATE_PER_KM);
        BigDecimal ratePerTon = decimalSetting(s, "pricing.ratePerTon", DEFAULT_RATE_PER_TON);

        double km = (distanceKm == null || distanceKm <= 0) ? DEFAULT_DISTANCE_KM : distanceKm;
        BigDecimal distanceCharge = BigDecimal.valueOf(km).multiply(ratePerKm);

        BigDecimal tons = weightKg == null
                ? BigDecimal.ZERO
                : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        BigDecimal weightCharge = tons.multiply(ratePerTon);

        BigDecimal subtotal = baseFare.add(distanceCharge).add(weightCharge);

        double vehicleMultiplier = multiplierFor(s, "pricing.vehicleTypeMultiplier", vehicleType);
        double priorityMultiplier = multiplierFor(s, "pricing.priorityMultiplier", priority);
        subtotal = subtotal.multiply(BigDecimal.valueOf(vehicleMultiplier))
                .multiply(BigDecimal.valueOf(priorityMultiplier));

        if (backhaul) {
            BigDecimal discountPct = backhaulDiscountPct(s);
            BigDecimal discountFactor = BigDecimal.ONE.subtract(
                    discountPct.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
            subtotal = subtotal.multiply(discountFactor);
        }

        return subtotal.setScale(2, RoundingMode.HALF_UP);
    }

    /** Itemized breakdown used by the "before you commit" estimate endpoint. */
    public Map<String, Object> estimateBreakdown(Double distanceKm, BigDecimal weightKg,
                                                 String vehicleType, String priority, boolean backhaul) {
        Map<String, String> s = getSettings();
        BigDecimal baseFare = decimalSetting(s, "pricing.baseFare", DEFAULT_BASE_FARE);
        BigDecimal ratePerKm = decimalSetting(s, "pricing.ratePerKm", DEFAULT_RATE_PER_KM);
        BigDecimal ratePerTon = decimalSetting(s, "pricing.ratePerTon", DEFAULT_RATE_PER_TON);

        double km = (distanceKm == null || distanceKm <= 0) ? DEFAULT_DISTANCE_KM : distanceKm;
        BigDecimal distanceCharge = BigDecimal.valueOf(km).multiply(ratePerKm);
        BigDecimal tons = weightKg == null
                ? BigDecimal.ZERO
                : weightKg.divide(new BigDecimal("1000"), 4, RoundingMode.HALF_UP);
        BigDecimal weightCharge = tons.multiply(ratePerTon);

        double vehicleMultiplier = multiplierFor(s, "pricing.vehicleTypeMultiplier", vehicleType);
        double priorityMultiplier = multiplierFor(s, "pricing.priorityMultiplier", priority);
        BigDecimal discountPctValue = backhaul ? backhaulDiscountPct(s) : BigDecimal.ZERO;

        BigDecimal subtotal = baseFare.add(distanceCharge).add(weightCharge);
        BigDecimal afterMultipliers = subtotal.multiply(BigDecimal.valueOf(vehicleMultiplier))
                .multiply(BigDecimal.valueOf(priorityMultiplier));
        BigDecimal discountAmount = afterMultipliers.multiply(
                discountPctValue.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP));
        BigDecimal finalAmount = afterMultipliers.subtract(discountAmount).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> breakdown = new HashMap<>();
        breakdown.put("baseFare", baseFare);
        breakdown.put("distanceKm", round2(BigDecimal.valueOf(km)));
        breakdown.put("distanceCharge", distanceCharge.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("weightKg", weightKg);
        breakdown.put("weightTon", tons.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("weightCharge", weightCharge.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("vehicleType", vehicleType);
        breakdown.put("vehicleTypeMultiplier", BigDecimal.valueOf(vehicleMultiplier));
        breakdown.put("priority", priority);
        breakdown.put("priorityMultiplier", BigDecimal.valueOf(priorityMultiplier));
        breakdown.put("backhaulDiscountPct", discountPctValue);
        breakdown.put("backhaulDiscountAmount", discountAmount.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("subtotal", subtotal.setScale(2, RoundingMode.HALF_UP));
        breakdown.put("estimatedAmount", finalAmount);
        return breakdown;
    }

    private static BigDecimal round2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }
}
