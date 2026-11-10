package com.occ.housecleaning.bdd.models;

/**
 * Lifecycle of a trade, shared by the raw audit and the contract legs.
 * <pre>
 *   (audit)  NOVATED | REJECTED
 *   (legs)   NOVATED -> SETTLED
 *            NOVATED -> FAILED -> SETTLED   (a failed settlement can be retried)
 * </pre>
 * SETTLED is terminal.
 */
public enum NovationStatus {
    NOVATED,
    REJECTED,
    SETTLED,
    FAILED
}
