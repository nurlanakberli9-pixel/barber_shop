package com.barber.booking;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Security;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PushSender {

    private static final Logger log = LoggerFactory.getLogger(PushSender.class);

    private final PushService service;
    private final PushSubscriptionRepository subs;

    public PushSender(VapidKeys keys, PushSubscriptionRepository subs)
            throws GeneralSecurityException {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.service = new PushService(keys.publicKey(), keys.privateKey(), keys.subject());
        this.subs = subs;
    }

    public int sendToShop(Long shopId, String payload) {
        int delivered = 0;
        for (PushSubscription s : subs.findByBarbershopId(shopId)) {
            try {
                Notification n = new Notification(s.getEndpoint(), s.getP256dh(), s.getAuth(),
                        payload.getBytes(StandardCharsets.UTF_8));
                int status = service.send(n).getStatusLine().getStatusCode();
                if (status >= 200 && status < 300) {
                    delivered++;
                } else if (status == 404 || status == 410) {
                    subs.deleteById(s.getId());
                } else {
                    log.warn("Push göndərilmədi, status {}", status);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return delivered;
            } catch (Exception e) {
                log.warn("Push göndərilmədi: {}", e.getClass().getSimpleName());
            }
        }
        return delivered;
    }

    public static String json(String title, String body, String tag) {
        return "{\"title\":\"" + esc(title) + "\",\"body\":\"" + esc(body)
                + "\",\"tag\":\"" + esc(tag) + "\"}";
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
