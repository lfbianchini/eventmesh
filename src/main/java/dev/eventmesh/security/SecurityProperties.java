package dev.eventmesh.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("eventmesh.security")
public class SecurityProperties {

  private String encryptionKey = "";

  public String getEncryptionKey() {
    return encryptionKey;
  }

  public void setEncryptionKey(String encryptionKey) {
    this.encryptionKey = encryptionKey;
  }
}
