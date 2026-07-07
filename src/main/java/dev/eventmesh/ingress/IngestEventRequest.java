package dev.eventmesh.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IngestEventRequest(
    @NotBlank
        @Size(max = 128)
        @Pattern(
            regexp = "^[a-z0-9]+(?:[._-][a-z0-9]+)*$",
            message = "must use lowercase dot-delimited identifier syntax")
        String type,
    @NotBlank @Size(max = 256) String source,
    @Size(max = 512) String subject,
    @NotNull JsonNode data) {

  public IngestEventRequest {
    if (data != null && data.isNull()) {
      data = null;
    }
  }
}
