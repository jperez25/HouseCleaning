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

/** The share of an exercise assigned to one short holder. */
@Entity
@Table(name = "option_assignments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptionAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exercise_id", nullable = false, length = 64)
    private String exerciseId;

    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
}
