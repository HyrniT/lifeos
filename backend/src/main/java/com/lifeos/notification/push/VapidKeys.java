package com.lifeos.notification.push;

import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;

/**
 * The VAPID key pair that identifies this server to push services.
 *
 * The public key is handed to the browser at subscribe time and is baked into the
 * subscription: **if the key changes, every existing subscription stops working**
 * and each user has to re-subscribe. So a generated pair is only ever a
 * development convenience — it is logged with instructions to pin it, and the
 * service warns on every start until it is configured.
 */
@Component
public class VapidKeys {

    private static final Logger log = LoggerFactory.getLogger(VapidKeys.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private final String publicKey;
    private final String privateKey;
    private final String subject;
    private final boolean configured;

    public VapidKeys(
            @Value("${lifeos.push.vapid.public-key:}") String configuredPublic,
            @Value("${lifeos.push.vapid.private-key:}") String configuredPrivate,
            @Value("${lifeos.push.vapid.subject:mailto:admin@lifeos.local}") String subject) {

        this.subject = subject;

        if (!configuredPublic.isBlank() && !configuredPrivate.isBlank()) {
            this.publicKey = configuredPublic.trim();
            this.privateKey = configuredPrivate.trim();
            this.configured = true;
            log.info("Web Push enabled with the configured VAPID key pair.");
            return;
        }

        KeyPair pair = generate();
        this.publicKey = encodePublic((ECPublicKey) pair.getPublic());
        this.privateKey = encodePrivate((ECPrivateKey) pair.getPrivate());
        this.configured = false;

        log.warn("""

                ============================================================
                 No VAPID key pair configured — generated an ephemeral one.
                 Every restart invalidates all push subscriptions.
                 Pin it by setting:
                   LIFEOS_PUSH_VAPID_PUBLIC_KEY={}
                   LIFEOS_PUSH_VAPID_PRIVATE_KEY={}
                ============================================================
                """, publicKey, privateKey);
    }

    private static KeyPair generate() {
        try {
            ECNamedCurveParameterSpec spec = ECNamedCurveTable.getParameterSpec("prime256v1");
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(spec);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not generate a VAPID key pair", ex);
        }
    }

    /** Uncompressed point (0x04 ‖ X ‖ Y), base64url — the format browsers expect. */
    private static String encodePublic(ECPublicKey key) {
        return base64Url(key.getQ().getEncoded(false));
    }

    private static String encodePrivate(ECPrivateKey key) {
        return base64Url(key.getD().toByteArray());
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String publicKey() {
        return publicKey;
    }

    public String privateKey() {
        return privateKey;
    }

    public String subject() {
        return subject;
    }

    /** False when the pair was generated at boot and will not survive a restart. */
    public boolean isPersistent() {
        return configured;
    }
}
