package dev.eventmesh.subscription;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSubscriptionRequest(
    @NotBlank @Size(max = 128) String name,
    @NotBlank @Size(max = 2048) String endpointUrl,
    @NotEmpty @Size(max = 50)
        List<
                @NotBlank
                @Size(max = 128)
                @Pattern(regexp = "^[a-z0-9]+(?:[._-][a-z0-9]+)*$") String>
            eventTypes,
    @Size(min = 32, max = 512) String signingSecret) {}
