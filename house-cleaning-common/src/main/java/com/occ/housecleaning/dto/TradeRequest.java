package com.occ.housecleaning.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound trade intake request from an exchange platform.
 */
public record TradeRequest(
    @NotBlank(message = "externalTradeId is required")
    String externalTradeId,

    @NotBlank(message = "exchangeCode is required")
    @Pattern(regexp = "^[A-Z0-9]{3,10}$", message = "exchangeCode must be 3-10 uppercase alphanumeric characters")
    String exchangeCode,

    @NotBlank(message = "optionSymbol is required")
    @Pattern(
        regexp = "^[A-Z]{1,6}\\d{6}[CP]\\d{8}$",
        message = "optionSymbol must conform to OCC standard format (e.g., AAPL260619C00150000)"
    )
    String optionSymbol,

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be greater than zero")
    Integer quantity,

    @NotNull(message = "executionPrice is required")
    @DecimalMin(value = "0.01", message = "executionPrice must be at least 0.01")
    @Digits(integer = 10, fraction = 4, message = "executionPrice format invalid (up to 10 integer digits and 4 decimals)")
    BigDecimal executionPrice,

    @NotBlank(message = "buyerMemberId is required")
    String buyerMemberId,

    @NotBlank(message = "sellerMemberId is required")
    String sellerMemberId,

    @NotNull(message = "executionTimestamp is required")
    @PastOrPresent(message = "executionTimestamp cannot be in the future")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant executionTimestamp
) {
    public TradeRequest {
        if (buyerMemberId != null && buyerMemberId.equalsIgnoreCase(sellerMemberId)) {
            throw new IllegalArgumentException("buyerMemberId and sellerMemberId cannot be the same clearing member");
        }
    }
}
