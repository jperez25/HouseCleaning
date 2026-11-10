package com.occ.housecleaning.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Sets the simulated volatility factor. With no {@code symbol} it applies globally; with one it overrides
 * the global value for that option symbol only.
 */
public record VolatilityShockRequest(
        @Pattern(regexp = "^[A-Z]{1,6}\\d{6}[CP]\\d{8}$",
                message = "symbol must conform to OCC standard format (e.g., AAPL260619C00150000)")
        String symbol,

        @NotNull(message = "factor is required")
        @DecimalMin(value = "0", inclusive = false, message = "factor must be greater than zero")
        @DecimalMax(value = "10", message = "factor must not exceed 10")
        @Digits(integer = 2, fraction = 6, message = "factor format invalid (up to 6 decimals)")
        BigDecimal factor
) {
}
