package dev.eventmesh.ingress;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CanonicalEventHasherTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final CanonicalEventHasher hasher = new CanonicalEventHasher(objectMapper);

  @Test
  void ignoresJsonObjectFieldOrderAtEveryDepth() throws Exception {
    var first =
        new IngestEventRequest(
            "order.created",
            "checkout",
            "order-1",
            objectMapper.readTree(
                """
                {"currency":"USD","customer":{"name":"Ada","id":7},"items":[{"sku":"a","qty":2}]}
                """));
    var reordered =
        new IngestEventRequest(
            "order.created",
            "checkout",
            "order-1",
            objectMapper.readTree(
                """
                {"items":[{"qty":2,"sku":"a"}],"customer":{"id":7,"name":"Ada"},"currency":"USD"}
                """));

    assertThat(hasher.hash(first)).isEqualTo(hasher.hash(reordered));
  }

  @Test
  void includesEnvelopeFieldsInTheHash() throws Exception {
    var data =
        objectMapper.readTree(
            """
            {"orderId":"order-1"}
            """);
    var first = new IngestEventRequest("order.created", "checkout", "order-1", data);
    var changed = new IngestEventRequest("order.cancelled", "checkout", "order-1", data);

    assertThat(hasher.hash(first)).isNotEqualTo(hasher.hash(changed));
  }
}
