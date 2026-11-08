package com.occ.housecleaning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Clearing member cash and margin balance ledger.
 */
@Entity
@Table(name = "member_ledger_balances")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberLedgerBalance {

    @Id
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "cash_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashBalance;

    @Column(name = "margin_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal marginBalance;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
