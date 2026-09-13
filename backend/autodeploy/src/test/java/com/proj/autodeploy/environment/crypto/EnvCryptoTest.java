package com.proj.autodeploy.environment.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AES-GCM 암복호화 단위 테스트. Spring 컨텍스트 없이 돈다.
 */
class EnvCryptoTest {

    private static final String KEY = "test-env-encryption-key-32bytes!";

    private final EnvCrypto crypto = new EnvCrypto(KEY);

    @Test
    @DisplayName("암호화 → 복호화 라운드트립에서 원문이 보존된다")
    void roundTrip() {
        String plain = "postgres://user:pw@localhost:5432/db";

        assertThat(crypto.decrypt(crypto.encrypt(plain))).isEqualTo(plain);
    }

    @Test
    @DisplayName("한글·빈 문자열도 왕복된다")
    void roundTripEdgeCases() {
        assertThat(crypto.decrypt(crypto.encrypt("한글 값 테스트"))).isEqualTo("한글 값 테스트");
        assertThat(crypto.decrypt(crypto.encrypt(""))).isEmpty();
    }

    @Test
    @DisplayName("같은 평문을 두 번 암호화하면 암호문이 다르다 (IV 가 매번 새로 생성된다)")
    void sameplaintextProducesDifferentCipherText() {
        String plain = "APP_PORT=8080";

        String first = crypto.encrypt(plain);
        String second = crypto.encrypt(plain);

        assertThat(first).isNotEqualTo(second);
        // 그래도 둘 다 같은 평문으로 복호화된다
        assertThat(crypto.decrypt(first)).isEqualTo(crypto.decrypt(second)).isEqualTo(plain);
    }

    @Test
    @DisplayName("암호문에 평문이 그대로 남지 않는다")
    void cipherTextDoesNotContainPlainText() {
        assertThat(crypto.encrypt("SUPER_SECRET_VALUE")).doesNotContain("SUPER_SECRET_VALUE");
    }

    @Test
    @DisplayName("조작된 암호문은 GCM 태그 검증에서 실패한다")
    void tamperedCipherTextIsRejected() {
        String encrypted = crypto.encrypt("original");
        // 마지막 글자를 바꿔 태그를 깨뜨린다
        char last = encrypted.charAt(encrypted.length() - 1);
        String tampered = encrypted.substring(0, encrypted.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> crypto.decrypt(tampered))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("다른 키로는 복호화할 수 없다")
    void wrongKeyCannotDecrypt() {
        String encrypted = crypto.encrypt("secret");
        EnvCrypto other = new EnvCrypto("another-env-encryption-key-32byt");

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("키 길이가 16/24/32 바이트가 아니면 기동 시점에 실패한다")
    void rejectsInvalidKeyLength() {
        assertThatThrownBy(() -> new EnvCrypto("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16, 24, or 32 bytes");
    }
}
