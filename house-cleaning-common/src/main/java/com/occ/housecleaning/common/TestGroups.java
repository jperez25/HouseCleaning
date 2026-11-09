package com.occ.housecleaning.common;

/**
 * TestNG group names shared by test classes and {@code testng.xml}.
 * Constants (not an enum) so they can be used as compile-time values in {@code @Test(groups = ...)}.
 */
public final class TestGroups {

    /** Fast, critical-path checks run on every build. */
    public static final String SMOKE = "smoke";

    /** Full suite; safe to run in parallel. */
    public static final String REGRESSION = "regression";

    /** Tests that depend on shared state and must not run in parallel with other tests. */
    public static final String SEQUENTIAL = "sequential";

    private TestGroups() {
    }
}
