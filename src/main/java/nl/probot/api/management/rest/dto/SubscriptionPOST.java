package nl.probot.api.management.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

public record SubscriptionPOST(

        @NotBlank
        @Size(max = 50)
        @Schema(example = "The A-Team")
        String subject
) {
}
