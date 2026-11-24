package com.barber.booking;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class RenderBlueprintTests {

    private static final List<String> SECRETS = List.of("DB_URL", "DB_USER", "DB_PASSWORD",
            "ADMIN_PASSWORD", "VAPID_PUBLIC_KEY", "VAPID_PRIVATE_KEY", "VAPID_SUBJECT");

    private static List<String> lines() throws IOException {
        return Files.readAllLines(Path.of("render.yaml")).stream().map(String::trim).toList();
    }

    @Test
    void blueprintTargetsTheFreeDockerWebService() throws IOException {
        List<String> l = lines();
        for (String wanted : List.of("runtime: docker", "plan: free", "region: frankfurt",
                "healthCheckPath: /actuator/health/liveness", "dockerfilePath: ./Dockerfile")) {
            assertTrue(l.contains(wanted), wanted);
        }
        int i = l.indexOf("- key: SPRING_PROFILES_ACTIVE");
        assertTrue(i >= 0);
        assertEquals("value: prod", l.get(i + 1));
    }

    @Test
    void secretsAreNeverStoredInTheBlueprint() throws IOException {
        List<String> l = lines();
        for (String key : SECRETS) {
            int i = l.indexOf("- key: " + key);
            assertTrue(i >= 0, key + " yoxdur");
            assertEquals("sync: false", l.get(i + 1), key);
        }
    }

    @Test
    void connectionPoolIsTunedForServerlessPostgresAndSmallMemory() throws IOException {
        String p = Files.readString(Path.of("src/main/resources/application.properties"));
        for (String line : List.of("spring.datasource.hikari.minimum-idle=0",
                "spring.datasource.hikari.idle-timeout=60000",
                "spring.datasource.hikari.max-lifetime=240000", "server.tomcat.threads.max=20")) {
            assertTrue(p.lines().anyMatch(x -> x.trim().equals(line)), line);
        }
        assertTrue(Files.readString(Path.of("render.yaml")).contains("-XX:MaxRAMPercentage="));
    }
}
