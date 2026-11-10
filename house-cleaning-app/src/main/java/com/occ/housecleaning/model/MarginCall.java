package com.occ.housecleaning.model;

import com.occ.housecleaning.dto.MarginCallStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Demand for more collateral. At most one OPEN call exists per member; it is updated while the
 * breach persists and closed when the shortfall clears.
 */
@Entity
@Table(name = "margin_calls")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarginCall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "margin_required", nullable = false, precision = 19, scale = 4)
    private BigDecimal marginRequired;

    @Column(name = "cash_available", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashAvailable;

    @Column(name = "shortfall", nullable = false, precision = 19, scale = 4)
    private BigDecimal shortfall;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MarginCallStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;
}
