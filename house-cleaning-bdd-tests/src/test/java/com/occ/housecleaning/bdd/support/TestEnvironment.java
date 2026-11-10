package com.occ.housecleaning.bdd.support;

/** Resolves where the deployed service under test lives. */
public final class TestEnvironment {

    private TestEnvironment() {
    }

    /** -Dtest.base.url, else TEST_BASE_URL, else http://localhost:8080. */
    public static String baseUrl() {
        String url = System.getProperty("test.base.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("TEST_BASE_URL");
        }
        return (url == null || url.isBlank()) ? "http://localhost:8080" : url;
    }
}
