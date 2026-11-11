package com.occ.housecleaning.reporting;

/** Uploads a finished report and returns the URL where it can be read. */
public interface ReportPublisher {
    String publish(String suite);
}
