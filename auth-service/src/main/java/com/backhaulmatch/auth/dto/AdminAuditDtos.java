package com.backhaulmatch.auth.dto;

/**
 * DTO used when posting audit events to admin-service's internal audit
 * endpoint. Field names must line up with admin-service's
 * InternalAuditRequest record — JSON is matched by name.
 */
public class AdminAuditDtos {

    public record AdminAuditRequest(
            String type,     // LOGIN | FAILED_LOGIN | USER_REGISTERED | USER_ACTIVITY
            Long userId,
            String username,
            String action,   // short verb, e.g. "login"
            String details   // longer context, e.g. "COURIER_OPERATOR signed in"
    ) {
        // admin-service's InternalAuditRequest also accepts an optional "source".
        public java.util.Map<String, Object> withSource(String source) {
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("type", type);
            map.put("userId", userId);
            map.put("username", username);
            map.put("action", action);
            map.put("details", details);
            map.put("source", source);
            return map;
        }
    }
}
