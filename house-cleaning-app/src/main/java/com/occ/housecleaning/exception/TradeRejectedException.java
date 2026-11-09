package com.occ.housecleaning.exception;

import com.occ.housecleaning.dto.RejectionReason;

/**
 * Thrown when a well-formed trade is refused for a business reason (duplicate, unknown member, ...).
 */
public class TradeRejectedException extends RuntimeException {

    private final RejectionReason reason;

    public TradeRejectedException(RejectionReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public RejectionReason getReason() {
        return reason;
    }
}
