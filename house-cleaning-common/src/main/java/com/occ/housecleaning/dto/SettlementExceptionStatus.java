package com.occ.housecleaning.dto;

public enum SettlementExceptionStatus {
    /** The settlement failed and has not been retried successfully. */
    OPEN,
    /** A later settlement attempt succeeded. */
    RESOLVED
}
