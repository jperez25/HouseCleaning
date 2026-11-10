package com.occ.housecleaning.model;

import com.occ.housecleaning.dto.SettlementExceptionStatus;
import com.occ.housecleaning.dto.SettlementFailureReason;
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

import java.time.Instant;

/**
 * A settlement attempt that failed for one member. Resolved automatically when a later attempt succeeds.
 */
@Entity
@Table(name = "settlement_exceptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementExceptionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_trade_id", nullable = false, length = 64)
    private String externalTradeId;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 32)
    private SettlementFailureReason reason;

    @Column(name = "detail", nullable = false, length = 255)
    private String detail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SettlementExceptionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;
}
