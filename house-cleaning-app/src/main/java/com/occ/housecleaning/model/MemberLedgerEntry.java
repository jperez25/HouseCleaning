package com.occ.housecleaning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Journal entry recording an individual credit or debit against a member's ledger.
 */
@Entity
@Table(name = "member_ledger_entries")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "external_trade_id", nullable = false, length = 64)
    private String externalTradeId;

    @Column(name = "entry_type", nullable = false, length = 16)
    private String entryType; // "DEBIT" or "CREDIT"

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
