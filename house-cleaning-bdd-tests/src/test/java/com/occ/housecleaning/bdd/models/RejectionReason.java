package com.occ.housecleaning.bdd.models;

/**
 * Business reasons a trade can be rejected during novation, with the HTTP status each maps to.
 */
public enum RejectionReason {
    DUPLICATE_TRADE(409),
    UNKNOWN_MEMBER(422),
    INACTIVE_MEMBER(422),
    INSUFFICIENT_FUNDS(422);

    private final int httpStatus;

    RejectionReason(int httpStatus) {
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
