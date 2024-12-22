package nl.probot.apim.core.rest.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;

public record SubscriptionPOST(

        @NotBlank
        @Size(max = 50)
        @Schema(example = "My Organisation", description = "the one that owns this subscription")
        String name,

        @Future
        @Schema(example = "2030-01-01", description = "when this subscription ends")
        LocalDate endDate,

        @NotEmpty
        @Size(max = 10)
        String[] accounts
) {
}
