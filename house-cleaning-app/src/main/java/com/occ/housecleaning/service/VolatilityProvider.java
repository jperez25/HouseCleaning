package com.occ.housecleaning.service;

import java.math.BigDecimal;

/**
 * Source of the simulated volatility factor that scales margin. Injectable so tests can control it.
 */
public interface VolatilityProvider {

    /** Current factor for the symbol: its own override, else the global shock, else the configured base. */
    BigDecimal factor(String optionSymbol);

    /**
     * Sets a shock for one symbol, or globally when {@code optionSymbol} is null.
     *
     * @return an action that restores the previous value, used if the surrounding transaction rolls back
     */
    Runnable applyShock(String optionSymbol, BigDecimal factor);
}
