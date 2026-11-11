package com.occ.housecleaning.reporting;

import java.time.Duration;

/** Outcome of one test run (one suite/module). */
public record TestRunSummary(String suite, int passed, int failed, int skipped, Duration duration) {

    public int total() {
        return passed + failed + skipped;
    }

    public boolean successful() {
        return failed == 0;
    }

    public String durationText() {
        long s = duration.toSeconds();
        return String.format("%d:%02d (%d.%03d s)", s / 60, s % 60, s, duration.toMillisPart());
    }
}
