package com.occ.housecleaning.dto;

import java.math.BigDecimal;

/** A short holder assigned part of an exercise, and the cash it pays. */
public record AssignmentResponse(String memberId, long quantity, BigDecimal amount) {
}
