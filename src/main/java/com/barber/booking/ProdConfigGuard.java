package com.barber.booking;

import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProdConfigGuard {

    public ProdConfigGuard(VapidKeys keys) {
        check(keys);
    }

    static void check(VapidKeys keys) {
        if (keys.ephemeral()) {
            throw new IllegalStateException(
                    "prod profilində VAPID_PUBLIC_KEY və VAPID_PRIVATE_KEY verilməlidir");
        }
        String subject = keys.subject();
        boolean valid = subject != null
                && (subject.startsWith("mailto:") || subject.startsWith("https://"))
                && !subject.toLowerCase(Locale.ROOT).contains("example.");
        if (!valid) {
            throw new IllegalStateException(
                    "VAPID_SUBJECT düzgün deyil: mailto:sizin@domen.tld və ya https://domen.tld verin");
        }
    }
}
