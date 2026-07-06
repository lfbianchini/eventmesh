package dev.eventmesh.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

  @Test
  void hashesApiKeysAsLowercaseSha256Hex() {
    assertThat(ApiKeyHasher.sha256("em_dev_local_key_change_me"))
        .isEqualTo("7d39abda7f79c867eef492a9884aaf7fdb1187cf5c6adb8818d7f069fc12a981");
  }
}

