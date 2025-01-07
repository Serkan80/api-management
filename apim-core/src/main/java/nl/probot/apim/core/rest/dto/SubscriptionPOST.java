package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import nl.probot.apim.core.entities.SubscriptionEntity;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static nl.probot.apim.commons.crypto.CryptoUtil.createRandomKey;

public record SubscriptionPOST(

        @NotBlank
        @Size(max = 50)
        @Pattern(regexp = "^[\\w\\d\\s_-]+$", message = "Name must only contain characters & digits and no special characters")
        @Schema(example = "My Organisation", description = "the one that owns this subscription")
        String name,

        @Future
        @Schema(example = "2030-01-01", description = "when this subscription ends")
        LocalDate endDate,

        @NotEmpty
        @Size(max = 20)
        @JsonDeserialize(using = StringToArrayDeserializer.class)
        String[] accounts) {

    @JsonIgnore
    @AssertTrue(message = "User Accounts must contain unique values")
    public boolean isAccountsUnique() {
        return Set.copyOf(List.of(this.accounts)).size() == this.accounts.length;
    }

    public SubscriptionEntity toEntity() {
        var result = new SubscriptionEntity();
        result.name = this.name;
        result.enabled = true;
        result.subscriptionKey = createRandomKey(32);
        result.createdAt = OffsetDateTime.now(ZoneId.of("Europe/Amsterdam"));
        result.endDate = this.endDate;
        result.accounts = this.accounts;
        return result;
    }
}
