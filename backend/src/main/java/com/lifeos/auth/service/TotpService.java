package com.lifeos.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * RFC 6238 TOTP, implemented directly rather than pulled from a library — it is
 * ~60 lines of well-specified maths and avoids another transitive dependency in
 * the authentication path.
 *
 * Compatible with Google Authenticator, Authy, 1Password and Microsoft Authenticator.
 */
@Service
public class TotpService {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;      // 160-bit, the RFC 4226 recommendation
    private static final int DIGITS = 6;
    private static final int PERIOD_SECONDS = 30;
    /** Accept the neighbouring windows so a slightly skewed phone clock still works. */
    private static final int ALLOWED_DRIFT_STEPS = 1;

    private final SecureRandom random = new SecureRandom();
    private final String issuer;

    public TotpService(@Value("${lifeos.totp.issuer:LifeOS}") String issuer) {
        this.issuer = issuer;
    }

    public String generateSecret() {
        byte[] buf = new byte[SECRET_BYTES];
        random.nextBytes(buf);
        return base32Encode(buf);
    }

    /** The otpauth:// URI the client turns into a QR code. */
    public String buildOtpAuthUri(String secret, String accountEmail) {
        String label = URLEncoder.encode(issuer + ":" + accountEmail, StandardCharsets.UTF_8);
        return "otpauth://totp/%s?secret=%s&issuer=%s&algorithm=SHA1&digits=%d&period=%d".formatted(
                label, secret, URLEncoder.encode(issuer, StandardCharsets.UTF_8), DIGITS, PERIOD_SECONDS);
    }

    public boolean verify(String secret, String code) {
        if (secret == null || code == null) {
            return false;
        }
        String normalised = code.trim().replace(" ", "");
        if (normalised.length() != DIGITS || !normalised.chars().allMatch(Character::isDigit)) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long step = Instant.now().getEpochSecond() / PERIOD_SECONDS;

        boolean match = false;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            // No early return: comparing every window keeps the runtime independent
            // of which window matched, so the check does not leak timing information.
            match |= constantTimeEquals(normalised, generateCode(key, step + drift));
        }
        return match;
    }

    public List<String> generateRecoveryCodes(int count) {
        List<String> codes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            byte[] buf = new byte[5];
            random.nextBytes(buf);
            StringBuilder sb = new StringBuilder();
            for (byte b : buf) {
                sb.append(BASE32.charAt(b & 0x1F));
            }
            // Grouped for readability: ABCDE-FGHIJ
            codes.add(sb.substring(0, 5) + "-" + base32Encode(shortRandom()).substring(0, 5));
        }
        return codes;
    }

    private byte[] shortRandom() {
        byte[] b = new byte[5];
        random.nextBytes(b);
        return b;
    }

    private String generateCode(byte[] key, long step) {
        byte[] data = new byte[8];
        long value = step;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                       | ((hash[offset + 1] & 0xFF) << 16)
                       | ((hash[offset + 2] & 0xFF) << 8)
                       | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP generation failed", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    // ---- Base32 (RFC 4648, no padding) -----------------------------------
    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return sb.toString();
    }

    static byte[] base32Decode(String encoded) {
        String clean = encoded.trim().replace("=", "").replace(" ", "").toUpperCase();
        int buffer = 0;
        int bitsLeft = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int idx = BASE32.indexOf(c);
            if (idx < 0) {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
