package com.occ.housecleaning.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Exercise of a long option position, cash-settled at the supplied underlying price.
 */
public record ExerciseRequest(
        @NotBlank(message = "memberId is required")
        String memberId,

        @NotBlank(message = "optionSymbol is required")
        @Pattern(regexp = "^[A-Z]{1,6}\\d{6}[CP]\\d{8}$",
                message = "optionSymbol must conform to OCC standard format (e.g., AAPL260619C00150000)")
        String optionSymbol,

        @NotNull(message = "quantity is required")
        @Positive(message = "quantity must be greater than zero")
        Integer quantity,

        @NotNull(message = "settlementPrice is required")
        @DecimalMin(value = "0.01", message = "settlementPrice must be at least 0.01")
        @Digits(integer = 10, fraction = 4, message = "settlementPrice format invalid (up to 10 integer digits and 4 decimals)")
        BigDecimal settlementPrice
) {
}
