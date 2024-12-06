package nl.probot.apim.core.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record SubscriptionPOST(

        @NotBlank
        @Size(max = 50)
        @Schema(example = "My Organisation", description = "the one that owns this subscription")
        String name
) {
}
