package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.util.Set;

public record SubscriptionPOST(

        @NotBlank
        @Size(max = 50)
        @Schema(example = "My Organisation", description = "the one that owns this subscription")
        String name,

        @Future
        @Schema(example = "2030-01-01", description = "when this subscription ends")
        LocalDate endDate,

        @NotEmpty
        @Size(max = 20)
        @JsonDeserialize(using = StringToArrayDeserializer.class)
        String[] accounts) {
        
    @AssertTrue(message = "Accounts must contain unique values")
    public boolean isAccountsUnique() {
        return Set.of(this.accounts).size() == this.accounts.length;
    }
}
