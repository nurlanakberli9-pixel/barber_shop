package com.barber.booking;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class PushConfig {

    private static final Logger log = LoggerFactory.getLogger(PushConfig.class);

    @Bean
    VapidKeys vapidKeys(@Value("${VAPID_PUBLIC_KEY:}") String publicKey,
            @Value("${VAPID_PRIVATE_KEY:}") String privateKey,
            @Value("${VAPID_SUBJECT:mailto:admin@example.com}") String subject)
            throws GeneralSecurityException {
        boolean hasPublic = !publicKey.isBlank();
        boolean hasPrivate = !privateKey.isBlank();
        if (hasPublic != hasPrivate) {
            throw new IllegalStateException(
                    "VAPID_PUBLIC_KEY və VAPID_PRIVATE_KEY birlikdə verilməlidir");
        }
        if (hasPublic) {
            return new VapidKeys(publicKey.trim(), privateKey.trim(), subject.trim(), false);
        }
        log.warn("VAPID açarları verilməyib: müvəqqəti açar yaradıldı (yalnız test üçün).");
        return ephemeral(subject.trim());
    }

    private static VapidKeys ephemeral(String subject) throws GeneralSecurityException {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) pair.getPublic();
        ECPrivateKey priv = (ECPrivateKey) pair.getPrivate();
        byte[] point = new byte[65];
        point[0] = 4;
        put(pub.getW().getAffineX(), point, 1);
        put(pub.getW().getAffineY(), point, 33);
        byte[] secret = new byte[32];
        put(priv.getS(), secret, 0);
        Base64.Encoder enc = Base64.getUrlEncoder().withoutPadding();
        return new VapidKeys(enc.encodeToString(point), enc.encodeToString(secret), subject, true);
    }

    private static void put(BigInteger value, byte[] target, int offset) {
        byte[] raw = value.toByteArray();
        int len = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - len, target, offset + 32 - len, len);
    }
}
