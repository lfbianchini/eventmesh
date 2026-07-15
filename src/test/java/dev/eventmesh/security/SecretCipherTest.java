package dev.eventmesh.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

  private static final String TEST_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

  @Test
  void encryptsWithRandomizedAuthenticatedEncryptionAndDecrypts() {
    var properties = new SecurityProperties();
    properties.setEncryptionKey(TEST_KEY);
    var cipher = new SecretCipher(properties);
    var plaintext = "a-test-signing-secret-that-is-long-enough".getBytes(StandardCharsets.UTF_8);

    var first = cipher.encrypt(plaintext);
    var second = cipher.encrypt(plaintext);

    assertThat(first).isNotEqualTo(second);
    assertThat(new String(cipher.decrypt(first), StandardCharsets.UTF_8))
        .isEqualTo("a-test-signing-secret-that-is-long-enough");
    assertThat(new String(cipher.decrypt(second), StandardCharsets.UTF_8))
        .isEqualTo("a-test-signing-secret-that-is-long-enough");
  }

  @Test
  void failsClosedForInvalidKeyMaterialAndCiphertext() {
    var invalidProperties = new SecurityProperties();
    invalidProperties.setEncryptionKey("not-base64!");
    assertThatThrownBy(() -> new SecretCipher(invalidProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("valid Base64");

    var properties = new SecurityProperties();
    properties.setEncryptionKey(TEST_KEY);
    var cipher = new SecretCipher(properties);
    assertThatThrownBy(() -> cipher.decrypt("dG9vLXNob3J0"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("malformed");
  }
}
