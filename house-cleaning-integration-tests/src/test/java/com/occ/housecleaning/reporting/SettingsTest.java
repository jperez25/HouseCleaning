package com.occ.housecleaning.reporting;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.testng.Assert.assertEquals;

/** Changes system properties, so it must not run in parallel with other tests (see testng.xml). */
public class SettingsTest {

    @AfterMethod(alwaysRun = true)
    public void clean() {
        System.clearProperty("config.dir");
        System.clearProperty("env");
        System.clearProperty("mail.host");
        Settings.reset();
    }

    @Test
    public void resolveSecuredOverEnvironmentAndSystemPropertyOverBoth() throws Exception {
        Path dir = Files.createTempDirectory("reporting-config");
        Files.writeString(dir.resolve("environment.t.json"),
                "{\"sendEmail\":true,\"mail\":{\"host\":\"file-host\",\"port\":2525}}");
        Files.writeString(dir.resolve("secured.t.json"), "{\"mail\":{\"host\":\"secret-host\",\"password\":\"pw\"}}");
        System.setProperty("config.dir", dir.toString());
        System.setProperty("env", "t");
        Settings.reset();

        assertEquals(Settings.get("sendEmail", "NO_SUCH_ENV_VAR", "false"), "true");
        assertEquals(Settings.get("mail.port", "NO_SUCH_ENV_VAR", "587"), "2525");
        assertEquals(Settings.get("mail.host", "NO_SUCH_ENV_VAR", ""), "secret-host");
        assertEquals(Settings.get("mail.password", "NO_SUCH_ENV_VAR", ""), "pw");

        System.setProperty("mail.host", "cli-host");
        assertEquals(Settings.get("mail.host", "NO_SUCH_ENV_VAR", ""), "cli-host");
    }

    @Test
    public void fallBackToDefaultWhenFilesAreMissing() throws Exception {
        System.setProperty("config.dir", Files.createTempDirectory("reporting-empty").toString());
        Settings.reset();
        assertEquals(Settings.get("mail.host", "NO_SUCH_ENV_VAR", "fallback"), "fallback");
    }
}
