package org.lhduc.registration.crypto;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public final class HmacUtil {
    private static final String ALGORITHM = "HmacSHA256";

    public static byte[] compute(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(), ALGORITHM);
            mac.init(key);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    public static boolean verify(String secret, byte[] data, byte[] expected) {
        byte[] computed = compute(secret, data);
        if (computed.length != expected.length) return false;
        for (int i = 0; i < computed.length; i++) {
            if (computed[i] != expected[i]) return false;
        }
        return true;
    }
}
