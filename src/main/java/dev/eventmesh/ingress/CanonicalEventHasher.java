package dev.eventmesh.ingress;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.eventmesh.auth.ApiKeyHasher;
import java.util.TreeMap;
import org.springframework.stereotype.Component;

@Component
public class CanonicalEventHasher {

  private final ObjectMapper objectMapper;

  public CanonicalEventHasher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public String hash(IngestEventRequest request) {
    var canonical = objectMapper.createObjectNode();
    canonical.put("type", request.type());
    canonical.put("source", request.source());
    if (request.subject() == null) {
      canonical.putNull("subject");
    } else {
      canonical.put("subject", request.subject());
    }
    canonical.set("data", canonicalize(request.data()));

    try {
      return ApiKeyHasher.sha256(objectMapper.writeValueAsString(canonical));
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("The event payload could not be canonicalized", exception);
    }
  }

  private JsonNode canonicalize(JsonNode node) {
    if (node.isObject()) {
      ObjectNode sorted = objectMapper.createObjectNode();
      var fields = new TreeMap<String, JsonNode>();
      node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
      fields.forEach((name, value) -> sorted.set(name, canonicalize(value)));
      return sorted;
    }

    if (node.isArray()) {
      ArrayNode canonicalArray = objectMapper.createArrayNode();
      node.forEach(value -> canonicalArray.add(canonicalize(value)));
      return canonicalArray;
    }

    return node.deepCopy();
  }
}
