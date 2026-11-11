package com.occ.housecleaning.reporting;

import org.testng.ISuite;
import org.testng.ISuiteListener;

import java.time.Duration;
import java.time.Instant;

/** Registered via META-INF/services: after the whole suite finishes, emails the summary if sendEmail=true. */
public class SendEmailSuiteListener implements ISuiteListener {

    private volatile Instant start = Instant.now();

    @Override
    public void onStart(ISuite suite) {
        start = Instant.now();
    }

    @Override
    public void onFinish(ISuite suite) {
        int passed = 0, failed = 0, skipped = 0;
        for (var result : suite.getResults().values()) {
            var ctx = result.getTestContext();
            passed += ctx.getPassedTests().size();
            failed += ctx.getFailedTests().size() + ctx.getFailedButWithinSuccessPercentageTests().size()
                    + ctx.getFailedConfigurations().size();   // a failed @Before* is a failure, as Maven counts it
            skipped += ctx.getSkippedTests().size();
        }
        RunReporter.afterRun(new TestRunSummary("integration-tests", passed, failed, skipped,
                Duration.between(start, Instant.now())));
    }
}
