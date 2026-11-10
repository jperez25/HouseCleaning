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

/** Record of one exercise of a long option position. */
@Entity
@Table(name = "option_exercises")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionExercise {

    @Id
    @Column(name = "exercise_id", nullable = false, length = 64)
    private String exerciseId;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "option_symbol", nullable = false, length = 32)
    private String optionSymbol;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "strike", nullable = false, precision = 19, scale = 4)
    private BigDecimal strike;

    @Column(name = "settlement_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal settlementPrice;

    @Column(name = "intrinsic_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal intrinsicValue;

    @Column(name = "total_payout", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalPayout;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
