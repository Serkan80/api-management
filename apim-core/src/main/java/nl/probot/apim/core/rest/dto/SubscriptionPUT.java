package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;

import java.time.LocalDate;

import static java.util.Objects.requireNonNullElse;

public record SubscriptionPUT(Boolean enabled, LocalDate endDate) {

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotEmpty() {
        return this.enabled != null || this.endDate != null;
    }

    @Override
    public String toString() {
        return "enabled=%s, endDate=%s".formatted(requireNonNullElse(this.enabled, "null"), requireNonNullElse(this.endDate, "null"));
    }
}
