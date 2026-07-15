package dev.eventmesh.security;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {

  private static final int NONCE_LENGTH = 12;
  private static final int TAG_LENGTH_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom secureRandom = new SecureRandom();

  public SecretCipher(SecurityProperties properties) {
    try {
      var decoded = Base64.getDecoder().decode(properties.getEncryptionKey());
      if (decoded.length != 32) {
        throw new IllegalStateException(
            "EVENTMESH_SECRET_ENCRYPTION_KEY must be a Base64-encoded 256-bit key");
      }
      this.key = new SecretKeySpec(decoded, "AES");
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "EVENTMESH_SECRET_ENCRYPTION_KEY must be valid Base64", exception);
    }
  }

  public String encrypt(byte[] plaintext) {
    var nonce = new byte[NONCE_LENGTH];
    secureRandom.nextBytes(nonce);
    try {
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      var encrypted = cipher.doFinal(plaintext);
      return Base64.getEncoder()
          .encodeToString(
              ByteBuffer.allocate(nonce.length + encrypted.length)
                  .put(nonce)
                  .put(encrypted)
                  .array());
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("Signing secret encryption failed", exception);
    }
  }

  public byte[] decrypt(String encoded) {
    try {
      var combined = Base64.getDecoder().decode(encoded);
      if (combined.length <= NONCE_LENGTH) {
        throw new IllegalStateException("Stored signing secret is malformed");
      }
      var nonce = new byte[NONCE_LENGTH];
      var encrypted = new byte[combined.length - NONCE_LENGTH];
      System.arraycopy(combined, 0, nonce, 0, nonce.length);
      System.arraycopy(combined, nonce.length, encrypted, 0, encrypted.length);
      var cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, nonce));
      return cipher.doFinal(encrypted);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new IllegalStateException("Stored signing secret could not be decrypted", exception);
    }
  }
}
