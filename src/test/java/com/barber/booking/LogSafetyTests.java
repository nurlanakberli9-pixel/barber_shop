package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LogSafetyTests {

    private static final Pattern CALL = Pattern.compile(
            "\\blog\\.(?:trace|debug|info|warn|error)\\s*\\((.*?)\\)\\s*;", Pattern.DOTALL);
    private static final Pattern STRING = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final List<String> BANNED = List.of("password", "phone", "token", "p256dh",
            "endpoint", "customername", "privatekey", "secret", "getmessage", "getcause", "stacktrace");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            return files.filter(f -> f.toString().endsWith(".java")).toList();
        }
    }

    @Test
    void logCallsNeverPassSensitiveValues() throws Exception {
        int calls = 0;
        for (Path p : sources()) {
            if (p.getFileName().toString().equals("DataSeeder.java")) continue;
            Matcher m = CALL.matcher(Files.readString(p));
            while (m.find()) {
                calls++;
                String args = STRING.matcher(m.group(1)).replaceAll("").toLowerCase(Locale.ROOT);
                for (String banned : BANNED) {
                    assertFalse(args.contains(banned), p.getFileName() + " loga '" + banned + "' ötürür: " + m.group(1));
                }
            }
        }
        assertTrue(calls >= 8, "log çağırışları tapılmadı, qayda səssizcə işləməyə bilər: " + calls);
    }

    @Test
    void noConsolePrintingOrStackTraceDumpsInMainCode() throws Exception {
        for (Path p : sources()) {
            String code = Files.readString(p);
            for (String bad : new String[] {"System.out", "System.err", "printStackTrace("}) {
                assertFalse(code.contains(bad), p.getFileName() + " içində " + bad);
            }
        }
    }

    @Test
    void seederIsDevOnlyAndProdConfigIsLockedDown() throws Exception {
        String seeder = Files.readString(Path.of("src/main/java/com/barber/booking/DataSeeder.java"));
        assertTrue(seeder.contains("@Profile(\"!prod\")"));
        String prod = Files.readString(Path.of("src/main/resources/application-prod.properties"));
        assertTrue(prod.contains("logging.structured.format.console=logstash"));
        assertTrue(prod.contains("spring.flyway.clean-disabled=true"));
        assertTrue(prod.contains("server.servlet.session.cookie.secure=true"));
        String main = Files.readString(Path.of("src/main/resources/application.properties"));
        assertTrue(main.contains("management.endpoints.web.exposure.include=health"));
        assertTrue(main.contains("management.endpoint.health.show-details=never"));
        assertTrue(main.contains("server.error.include-stacktrace=never"));
    }
}
