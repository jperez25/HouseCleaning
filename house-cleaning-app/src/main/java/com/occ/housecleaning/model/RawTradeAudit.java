package com.occ.housecleaning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Audit record of inbound raw trade execution records for regulatory compliance and audit trails.
 */
@Entity
@Table(name = "raw_trades_audit")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawTradeAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_trade_id", nullable = false, length = 64)
    private String externalTradeId;

    @Column(name = "exchange_code", nullable = false, length = 16)
    private String exchangeCode;

    @Lob
    @Column(name = "raw_payload", nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;
}
