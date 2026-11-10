package com.occ.housecleaning.dto;

public enum MarginCallStatus {
    /** Margin required exceeds the member's available cash. */
    OPEN,
    /** The shortfall was cleared (cash restored or exposure reduced). */
    CLOSED
}
