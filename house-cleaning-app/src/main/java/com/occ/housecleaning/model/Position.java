package com.occ.housecleaning.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
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
 * A member's net holding in one option symbol: positive is long (bought), negative is short (sold).
 * Updated on every novation under the member's balance row lock.
 */
@Entity
@Table(name = "positions", uniqueConstraints = @UniqueConstraint(
        name = "uk_position_member_symbol", columnNames = {"member_id", "option_symbol"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "option_symbol", nullable = false, length = 32)
    private String optionSymbol;

    @Column(name = "net_quantity", nullable = false)
    private long netQuantity;

    /** Execution price of the most recent trade in this symbol; the price basis for margin. */
    @Column(name = "last_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal lastPrice;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
