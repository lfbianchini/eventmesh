package dev.eventmesh.webhook;

import dev.eventmesh.security.SecurityProperties;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({WebhookProperties.class, SecurityProperties.class})
public class WebhookConfiguration {

  @Bean
  HttpClient webhookHttpClient(WebhookProperties properties) {
    return HttpClient.newBuilder()
        .connectTimeout(properties.getConnectTimeout())
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }
}
