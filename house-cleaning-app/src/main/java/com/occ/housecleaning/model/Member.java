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

/**
 * Registered clearing member. Only active members may participate in novated trades.
 * {@code creditLimit} is how far below zero the member's cash balance may go.
 */
@Entity
@Table(name = "clearing_members")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Member {

    @Id
    @Column(name = "member_id", nullable = false, length = 64)
    private String memberId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit;
}
