package com.occ.housecleaning.reporting;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Placeholder: nothing is uploaded. It only builds the URL the report would have, from {@code REPORT_BASE_URL}
 * (or {@code -DreportBaseUrl}). Replace it with a real uploader (S3, an artifact server...) when one exists.
 */
public class MockReportPublisher implements ReportPublisher {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final String baseUrl;

    public MockReportPublisher(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public String publish(String suite) {
        return baseUrl + "/" + suite + "/" + STAMP.format(Instant.now()) + "/index.html";
    }
}
