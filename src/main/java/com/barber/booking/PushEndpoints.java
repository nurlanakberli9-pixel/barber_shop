package com.barber.booking;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;

final class PushEndpoints {

    private static final List<String> HOSTS = List.of(
            "fcm.googleapis.com",
            "updates.push.services.mozilla.com",
            "push.services.mozilla.com",
            "push.apple.com",
            "notify.windows.com");

    private PushEndpoints() {}

    static boolean allowed(String endpoint) {
        if (endpoint == null || endpoint.length() > 600) return false;
        try {
            URI u = new URI(endpoint);
            if (!"https".equalsIgnoreCase(u.getScheme()) || u.getRawUserInfo() != null) return false;
            if (u.getPort() != -1 && u.getPort() != 443) return false;
            String host = u.getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.ROOT);
            for (String h : HOSTS) {
                if (host.equals(h) || host.endsWith("." + h)) return true;
            }
            return false;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
