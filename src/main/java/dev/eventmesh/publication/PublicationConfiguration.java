package dev.eventmesh.publication;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class PublicationConfiguration {

  public static final String ACCEPTED_EVENTS_TOPIC = "eventmesh.events.v1";

  @Bean
  @ConditionalOnProperty(
      name = "eventmesh.kafka.create-topics", havingValue = "true", matchIfMissing = true)
  NewTopic acceptedEventsTopic() {
    return TopicBuilder.name(ACCEPTED_EVENTS_TOPIC).partitions(6).replicas(1).build();
  }
}
