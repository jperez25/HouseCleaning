package com.occ.housecleaning.model;

import com.occ.housecleaning.dto.LegType;
import com.occ.housecleaning.dto.NovationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Novated contract leg record linking buyer or seller to the central counterparty (OCC).
 */
@Entity
@Table(name = "novated_contracts", uniqueConstraints = @UniqueConstraint(
        name = "uk_novated_trade_leg", columnNames = {"external_trade_id", "leg_type"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NovatedContract {

    @Id
    @Column(name = "leg_id", nullable = false, length = 64)
    private String legId;

    @Column(name = "external_trade_id", nullable = false, length = 64)
    private String externalTradeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", nullable = false, length = 32)
    private LegType legType;

    @Column(name = "clearing_member_id", nullable = false, length = 64)
    private String clearingMemberId;

    @Column(name = "counterparty_id", nullable = false, length = 64)
    private String counterpartyId;

    @Column(name = "buyer_member_id", nullable = false, length = 64)
    private String buyerMemberId;

    @Column(name = "seller_member_id", nullable = false, length = 64)
    private String sellerMemberId;

    @Column(name = "option_symbol", nullable = false, length = 32)
    private String optionSymbol;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "execution_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal executionPrice;

    @Column(name = "execution_timestamp", nullable = false)
    private Instant executionTimestamp;

    @Column(name = "novated_at", nullable = false)
    private Instant novatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private NovationStatus status;

    /** Set when the leg reaches SETTLED. */
    @Column(name = "settled_at")
    private Instant settledAt;
}
