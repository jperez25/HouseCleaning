package com.occ.housecleaning.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/** Default provider: a base factor from {@code risk.volatility.base-factor} plus runtime shocks held in memory. */
@Component
public class ConfigurableVolatilityProvider implements VolatilityProvider {

    private final BigDecimal baseFactor;
    private final AtomicReference<BigDecimal> globalShock = new AtomicReference<>();
    private final Map<String, BigDecimal> symbolShocks = new ConcurrentHashMap<>();

    public ConfigurableVolatilityProvider(@Value("${risk.volatility.base-factor:0.10}") BigDecimal baseFactor) {
        this.baseFactor = baseFactor;
    }

    @Override
    public BigDecimal factor(String optionSymbol) {
        BigDecimal symbolShock = symbolShocks.get(optionSymbol);
        if (symbolShock != null) {
            return symbolShock;
        }
        BigDecimal global = globalShock.get();
        return global != null ? global : baseFactor;
    }

    @Override
    public Runnable applyShock(String optionSymbol, BigDecimal factor) {
        if (optionSymbol == null) {
            BigDecimal previous = globalShock.getAndSet(factor);
            return () -> globalShock.set(previous);
        }
        BigDecimal previous = symbolShocks.put(optionSymbol, factor);
        return () -> {
            if (previous == null) {
                symbolShocks.remove(optionSymbol);
            } else {
                symbolShocks.put(optionSymbol, previous);
            }
        };
    }
}
