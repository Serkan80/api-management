package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

import static java.util.Objects.requireNonNullElse;

public record SubscriptionPUT(Boolean enabled, LocalDate endDate, @JsonDeserialize(using = StringArrayDeserializer.class) String[] accounts) {

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotEmpty() {
        return this.enabled != null || this.endDate != null || (this.accounts != null && this.accounts.length > 0);
    }

    @Override
    public String toString() {
        return "enabled=%s, endDate=%s".formatted(requireNonNullElse(this.enabled, "null"), requireNonNullElse(this.endDate, "null"));
    }
}
