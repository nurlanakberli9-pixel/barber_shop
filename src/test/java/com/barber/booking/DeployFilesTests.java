package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DeployFilesTests {

    private static final Pattern SECRET_ASSIGNMENT =
            Pattern.compile("(PASSWORD|PRIVATE_KEY|SECRET)=[A-Za-z0-9_-]{20,}");

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path));
    }

    private static boolean hasLine(String text, String wanted) {
        return text.lines().anyMatch(l -> l.trim().equals(wanted));
    }

    @Test
    void dockerfileIsMultiStageNonRootWithHealthcheck() throws IOException {
        String d = read("Dockerfile");
        assertTrue(d.contains("AS build"));
        assertTrue(d.contains("FROM eclipse-temurin:21-jre-alpine"));
        assertTrue(d.contains("USER app"));
        assertTrue(d.contains("HEALTHCHECK"));
        assertTrue(d.contains("/actuator/health/liveness"));
        assertTrue(d.contains("-XX:+ExitOnOutOfMemoryError"));
        assertFalse(d.contains("USER root"));
        assertFalse(d.contains("ADD http"));
        assertFalse(d.contains("COPY .env"));
    }

    @Test
    void deployFilesContainNoSecrets() throws IOException {
        List<Path> files = new ArrayList<>(List.of(Path.of("Dockerfile"), Path.of(".env.example"),
                Path.of(".dockerignore")));
        for (String dir : new String[] {"scripts", "docs", "src/main/resources", ".github"}) {
            try (Stream<Path> s = Files.walk(Path.of(dir))) {
                s.filter(Files::isRegularFile).forEach(files::add);
            }
        }
        for (Path f : files) {
            String text = Files.readString(f);
            assertFalse(text.contains("-----BEGIN"), f + " içində açar var");
            assertFalse(SECRET_ASSIGNMENT.matcher(text).find(), f + " içində gizli dəyər var");
        }
    }

    @Test
    void dockerignoreKeepsSecretsAndHistoryOutOfTheImage() throws IOException {
        String d = read(".dockerignore");
        for (String line : List.of(".git", ".env", ".secrets.local", "target", "backups")) {
            assertTrue(hasLine(d, line), line);
        }
    }

    @Test
    void gitignoreAndEnvExampleProtectSecrets() throws IOException {
        String g = read(".gitignore");
        for (String line : List.of(".env", "backups/", ".secrets.local")) {
            assertTrue(hasLine(g, line), line);
        }
        String e = read(".env.example");
        for (String key : List.of("SPRING_PROFILES_ACTIVE=prod", "DB_URL=", "ADMIN_PASSWORD=",
                "VAPID_PUBLIC_KEY=", "VAPID_PRIVATE_KEY=", "VAPID_SUBJECT=")) {
            assertTrue(e.contains(key), key);
        }
        for (String line : e.split("\\R")) {
            if (line.contains("PRIVATE_KEY=") || line.contains("ADMIN_PASSWORD=")
                    || line.contains("DB_PASSWORD=")) {
                String value = line.substring(line.indexOf('=') + 1).trim();
                assertTrue(value.length() <= 12, "nümunədə real dəyər ola bilməz: " + line);
            }
        }
    }

    @Test
    void deployPropertiesArePresent() throws IOException {
        String p = read("src/main/resources/application.properties");
        assertTrue(p.contains("server.port=${PORT:8080}"));
        assertTrue(p.contains("server.shutdown=graceful"));
        String prod = read("src/main/resources/application-prod.properties");
        assertTrue(prod.contains("server.servlet.session.cookie.secure=true"));
        assertTrue(prod.contains("logging.structured.format.console=logstash"));
    }

    @Test
    void helperScriptsAndDocsExist() throws IOException {
        for (String f : List.of("scripts/backup.sh", "scripts/restore-check.sh", "scripts/smoke.sh",
                "scripts/db-env.sh", "scripts/gen-secrets.py", "docs/DEPLOY.md")) {
            assertTrue(Files.isRegularFile(Path.of(f)), f);
        }
        assertTrue(read("scripts/smoke.sh").startsWith("#!/usr/bin/env bash"));
    }
}
