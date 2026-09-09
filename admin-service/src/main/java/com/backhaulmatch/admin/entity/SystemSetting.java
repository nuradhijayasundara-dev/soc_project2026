package com.backhaulmatch.admin.entity;

import jakarta.persistence.*;
import lombok.Data;

/** One tunable platform setting — pricing rules, matching preferences, notification options. */
@Entity
@Table(name = "system_settings")
@Data
public class SystemSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", unique = true, nullable = false)
    private String key;

    @Column(name = "setting_value", nullable = false)
    private String value;

    private String description;

    // PRICING | MATCHING | NOTIFICATION
    private String category;
}
