package com.occ.housecleaning.reporting;

import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Called once after a test run. Does nothing unless {@code sendEmail} is true (system property {@code sendEmail}
 * or env {@code SEND_EMAIL}; default false). Otherwise it "publishes" the report (mock URL) and emails a table with
 * total, passed, failed, skipped and duration. Failures here are logged and never fail the test run.
 *
 * <pre>
 *   sendEmail / SEND_EMAIL                  true to send (default false)
 *   mail.host, mail.port (587)  / SMTP_HOST, SMTP_PORT   465 = implicit TLS, otherwise STARTTLS unless mail.starttls=false
 *   mail.username, mail.password / SMTP_USERNAME, SMTP_PASSWORD   optional (no auth when the username is empty)
 *   mail.to, mail.from / MAIL_TO, MAIL_FROM   from defaults to the username
 *   reportBaseUrl / REPORT_BASE_URL         base of the (mock) report URL
 * </pre>
 * Each setting is resolved by {@link Settings}: system property, environment, then the config JSON files.
 */
public final class RunReporter {

    private RunReporter() {
    }

    public static boolean enabled() {
        return Boolean.parseBoolean(Settings.get("sendEmail", "SEND_EMAIL", "false"));
    }

    public static void afterRun(TestRunSummary summary) {
        if (!enabled()) return;
        try {
            String url = new MockReportPublisher(
                    Settings.get("reportBaseUrl", "REPORT_BASE_URL", "https://reports.example.com/house-cleaning"))
                    .publish(summary.suite());
            send(summary, url);
        } catch (Exception e) {
            System.err.println("[RunReporter] could not send the report email: " + e);
        }
    }

    private static void send(TestRunSummary s, String reportUrl) throws Exception {
        String host = require("mail.host", "SMTP_HOST");
        String to = require("mail.to", "MAIL_TO");
        int port = Integer.parseInt(Settings.get("mail.port", "SMTP_PORT", "587"));
        String user = Settings.get("mail.username", "SMTP_USERNAME", "");
        String password = Settings.get("mail.password", "SMTP_PASSWORD", "");
        String from = Settings.get("mail.from", "MAIL_FROM", user);
        if (from.isEmpty()) throw new IllegalStateException("set MAIL_FROM or SMTP_USERNAME");

        Properties p = new Properties();
        p.put("mail.smtp.host", host);
        p.put("mail.smtp.port", String.valueOf(port));
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "15000");
        p.put("mail.smtp.writetimeout", "15000");
        if (port == 465) {
            p.put("mail.smtp.ssl.enable", "true");
        } else if (!"false".equalsIgnoreCase(Settings.get("mail.starttls", "SMTP_STARTTLS", "true"))) {
            p.put("mail.smtp.starttls.enable", "true");
        }
        Session session;
        if (user.isEmpty()) {
            session = Session.getInstance(p);
        } else {
            p.put("mail.smtp.auth", "true");
            session = Session.getInstance(p, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(user, password);
                }
            });
        }

        MimeMessage m = new MimeMessage(session);
        m.setFrom(new InternetAddress(from));
        m.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        m.setSubject("[" + (s.successful() ? "PASSED" : "FAILED") + "] HouseCleaning " + s.suite()
                + ": " + s.passed() + "/" + s.total() + " passed");
        m.setContent(html(s, reportUrl), "text/html; charset=UTF-8");
        Transport.send(m);
        System.out.println("[RunReporter] report email sent to " + to);
    }

    static String html(TestRunSummary s, String reportUrl) {
        String color = s.successful() ? "#1a7f37" : "#cf222e";
        return "<p><b style=\"color:" + color + "\">" + (s.successful() ? "PASSED" : "FAILED") + "</b> &ndash; "
                + escape(s.suite()) + "</p>"
                + "<table border=\"1\" cellpadding=\"6\" cellspacing=\"0\" style=\"border-collapse:collapse\">"
                + "<tr><th>Total</th><th>Passed</th><th>Failed</th><th>Skipped</th><th>Time</th></tr>"
                + "<tr><td>" + s.total() + "</td><td>" + s.passed() + "</td><td>" + s.failed()
                + "</td><td>" + s.skipped() + "</td><td>" + s.durationText() + "</td></tr></table>"
                + "<p>Report: <a href=\"" + escape(reportUrl) + "\">" + escape(reportUrl) + "</a></p>";
    }

    private static String require(String key, String env) {
        String v = Settings.get(key, env, "");
        if (v.isEmpty()) throw new IllegalStateException(key + " is not set (-D" + key + ", " + env + " or the config JSON files)");
        return v;
    }

    private static String escape(String v) {
        return v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
