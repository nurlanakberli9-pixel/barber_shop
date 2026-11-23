package com.barber.booking;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdDatabaseGuard {

    public ProdDatabaseGuard(@Value("${spring.datasource.url}") String url) {
        check(url);
    }

    static void check(String url) {
        if (url == null || !url.startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException(
                    "prod profilində PostgreSQL lazımdır: DB_URL=jdbc:postgresql://... verin");
        }
    }
}
