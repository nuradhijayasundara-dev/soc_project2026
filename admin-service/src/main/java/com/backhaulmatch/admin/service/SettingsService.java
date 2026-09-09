package com.backhaulmatch.admin.service;

import com.backhaulmatch.admin.dto.AdminDtos.SettingUpdate;
import com.backhaulmatch.admin.entity.SystemSetting;
import com.backhaulmatch.admin.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SettingsService implements ApplicationRunner {

    private final SystemSettingRepository repository;
    private final AdminAuditService auditService;

    private static final Map<String, String[]> DEFAULTS = new LinkedHashMap<>() {{
        put("pricing.baseFare", new String[]{"1000", "Base fare (LKR)", "PRICING"});
        put("pricing.ratePerKm", new String[]{"150", "Rate per km (LKR)", "PRICING"});
        put("pricing.ratePerTon", new String[]{"300", "Rate per ton (LKR)", "PRICING"});
        put("pricing.ratePerTonFallback", new String[]{"500", "Flat per-ton rate when a route is off-table (LKR)", "PRICING"});
        put("pricing.vehicleTypeMultiplier", new String[]{"STANDARD:1.0,REFRIGERATED:1.3,FLATBED:1.2,BOX_TRUCK:1.1,TANKER:1.4", "Vehicle-type price multipliers", "PRICING"});
        put("pricing.priorityMultiplier", new String[]{"NORMAL:1.0,PRIORITY:1.25,URGENT:1.5", "Priority price multipliers", "PRICING"});
        put("pricing.backhaulDiscountPct", new String[]{"10", "Empty return-leg (backhaul) discount %", "PRICING"});
        put("notification.emailEnabled", new String[]{"false", "Send email in addition to in-app notifications", "NOTIFICATION"});
        put("notification.emailFrom", new String[]{"no-reply@backhaul-match.local", "From address for notification emails", "NOTIFICATION"});
    }};

    /** Seeds the default settings the first time the service starts. */
    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() == 0) {
            DEFAULTS.forEach((key, meta) -> {
                SystemSetting s = new SystemSetting();
                s.setKey(key);
                s.setValue(meta[0]);
                s.setDescription(meta[1]);
                s.setCategory(meta[2]);
                repository.save(s);
            });
        }
    }

    public List<SystemSetting> list() {
        return repository.findAllByOrderByCategoryAscKeyAsc();
    }

    /** Replaces the provided keys, keeps everything else. Used by the Settings page save. */
    @Transactional
    public List<SystemSetting> update(List<SettingUpdate> updates, String adminUsername) {
        for (SettingUpdate update : updates) {
            SystemSetting setting = repository.findByKey(update.key()).orElseGet(() -> {
                SystemSetting s = new SystemSetting();
                s.setKey(update.key());
                s.setCategory("GENERAL");
                s.setDescription("");
                return s;
            });
            setting.setValue(update.value());
            repository.save(setting);

            auditService.record("SETTING_UPDATED", null, adminUsername, "update setting",
                    "Setting " + update.key() + " changed to " + update.value(), "admin-service");
        }
        return list();
    }
}
