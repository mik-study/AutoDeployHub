package com.proj.autodeploy.environment.crypto;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 환경변수 값 대칭키 암복호화. (AES-256/GCM)
 *
 * <p>GCM 을 고른 이유는 무결성 태그가 함께 붙기 때문이다. CBC 였다면 저장된 암호문이 조작돼도
 * 복호화가 "그럴듯한 쓰레기"를 돌려주지만, GCM 은 태그 검증에 실패하면서 예외를 던진다.
 *
 * <p>저장 포맷은 {@code base64(iv(12B) + ciphertext + tag(16B))} 한 덩어리다. IV 를 앞에 붙여
 * 같이 저장하므로 값마다 IV 가 달라진다 — 같은 평문을 두 번 저장해도 암호문이 달라야 한다.
 *
 * <p>키는 {@code env.encryption-key} 로 받고 운영에서는 {@code ENV_ENC_KEY} 환경변수로 주입한다
 * (JWT_SECRET 과 같은 원칙). MVP 라 앱 대칭키를 쓰지만, 운영 전환 시 KMS 등으로 교체할 자리다.
 *
 * <p>⚠️ 키를 바꾸면 기존에 저장된 값은 복호화할 수 없다. 키 교체 시 재암호화 마이그레이션이 필요하다.
 */
@Component
public class EnvCrypto {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;        // GCM 권장 IV 길이(바이트)
    private static final int TAG_LENGTH_BITS = 128; // GCM 인증 태그 길이(비트)

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public EnvCrypto(@Value("${env.encryption-key}") String encryptionKey) {
        byte[] raw = encryptionKey.getBytes(StandardCharsets.UTF_8);
        if (raw.length != 16 && raw.length != 24 && raw.length != 32) {
            throw new IllegalStateException(
                    "env.encryption-key must be 16, 24, or 32 bytes (UTF-8), but was " + raw.length);
        }
        this.key = new SecretKeySpec(raw, ALGORITHM);
    }

    /** 평문 → base64(iv + ciphertext + tag). */
    public String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // 평문이 예외 메시지에 섞이지 않도록 원문을 절대 넣지 않는다.
            throw new IllegalStateException("failed to encrypt environment variable value", e);
        }
    }

    /** base64(iv + ciphertext + tag) → 평문. 값이 조작됐으면 GCM 태그 검증에서 실패한다. */
    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = new byte[IV_LENGTH];
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("failed to decrypt environment variable value", e);
        }
    }
}
