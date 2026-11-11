package com.occ.housecleaning.reporting;

import org.testng.annotations.Test;

import java.time.Duration;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class RunReporterTest {

    @Test
    public void buildSummaryTableShowsTotalPassedFailedAndTime() {
        var s = new TestRunSummary("bdd-tests", 8, 1, 1, Duration.ofSeconds(75));
        assertEquals(s.total(), 10);
        String html = RunReporter.html(s, "https://reports.example.com/x/index.html");
        assertTrue(html.contains("<td>10</td><td>8</td><td>1</td><td>1</td><td>1:15"));
        assertTrue(html.contains("FAILED"));
        assertTrue(html.contains("https://reports.example.com/x/index.html"));
    }

    @Test
    public void buildMockReportUrlFromBase() {
        assertTrue(new MockReportPublisher("https://r.example.com/").publish("a")
                .matches("https://r\\.example\\.com/a/\\d{8}-\\d{6}/index\\.html"));
    }
}
