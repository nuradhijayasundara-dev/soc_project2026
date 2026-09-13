package com.backhaulmatch.admin.service;

import com.backhaulmatch.admin.client.CourierServiceClient;
import com.backhaulmatch.admin.client.FleetServiceClient;
import com.backhaulmatch.admin.dto.AdminDtos.CompanyListResponse;
import com.backhaulmatch.admin.dto.AdminDtos.CompanyView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CompanyApprovalService {

    private final CourierServiceClient courierServiceClient;
    private final FleetServiceClient fleetServiceClient;
    private final AdminAuditService auditService;

    public CompanyListResponse listCompanies() {
        return new CompanyListResponse(
                courierServiceClient.listCompanies().stream().map(c -> toView(c, "courier")).toList(),
                fleetServiceClient.listCompanies().stream().map(c -> toView(c, "fleet")).toList()
        );
    }

    public CompanyView approve(String type, Long id, String adminUsername) {
        return setStatus(type, id, "approve", adminUsername);
    }

    public CompanyView reject(String type, Long id, String adminUsername) {
        return setStatus(type, id, "reject", adminUsername);
    }

    private CompanyView setStatus(String type, Long id, String action, String adminUsername) {
        Map<String, Object> company = switch (type.toLowerCase()) {
            case "courier" -> courierServiceClient.setStatus(id, action);
            case "fleet" -> fleetServiceClient.setStatus(id, action);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown company type: " + type);
        };

        auditService.record("COMPANY_" + action.toUpperCase(), null, adminUsername, action + " company",
                type + " company \"" + company.get("companyName") + "\" was " + action + "d", "admin-service");
        return toView(company, type);
    }

    private CompanyView toView(Map<String, Object> c, String type) {
        return new CompanyView(
                ((Number) c.get("id")).longValue(),
                ((Number) c.get("userId")).longValue(),
                (String) c.get("companyName"),
                (String) c.get("registrationNo"),
                (String) c.get("contactPhone"),
                (String) c.get("address"),
                (String) c.get("approvalStatus"),
                c.get("createdAt") == null ? null : String.valueOf(c.get("createdAt"))
        );
    }
}
