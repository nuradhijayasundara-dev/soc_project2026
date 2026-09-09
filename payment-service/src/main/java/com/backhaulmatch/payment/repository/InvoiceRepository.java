package com.backhaulmatch.payment.repository;

import com.backhaulmatch.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByCourierUserIdOrderByCreatedAtDesc(Long courierUserId);
    List<Invoice> findByFleetCompanyIdOrderByCreatedAtDesc(Long fleetCompanyId);

    long countByFleetCompanyId(Long fleetCompanyId);
    long countByFleetCompanyIdAndStatus(Long fleetCompanyId, Invoice.Status status);
    long countByCourierUserIdAndStatus(Long courierUserId, Invoice.Status status);

    // Sums are null when there are zero matching rows — callers handle that.
    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.fleetCompanyId = :companyId AND i.status = :status")
    BigDecimal sumAmountByFleetCompanyIdAndStatus(@Param("companyId") Long companyId, @Param("status") Invoice.Status status);

    // Fleet dashboard "Revenue (MTD)" — same sum, but only invoices created since the start of this month.
    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.fleetCompanyId = :companyId AND i.status = :status AND i.createdAt >= :from")
    BigDecimal sumAmountByFleetCompanyIdAndStatusAfter(@Param("companyId") Long companyId, @Param("status") Invoice.Status status, @Param("from") LocalDateTime from);

    @Query("SELECT SUM(i.amount) FROM Invoice i WHERE i.courierUserId = :courierUserId AND i.status = :status")
    BigDecimal sumAmountByCourierUserIdAndStatus(@Param("courierUserId") Long courierUserId, @Param("status") Invoice.Status status);
}
