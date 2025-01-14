package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

public record SubscriptionPUT(
        Boolean enabled,
        LocalDate endDate,

        @Size(min = 1, max = 20)
        Set<String> accounts) {

    public String[] accountAsArray() {
        if (this.accounts == null) {
            return null;
        }

        return this.accounts.stream().map(String::strip).toArray(String[]::new);
    }

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotEmpty() {
        return this.enabled != null || this.endDate != null || (this.accounts != null && !this.accounts.isEmpty());
    }

    @Override
    public String toString() {
        return "enabled=%s, endDate=%s".formatted(requireNonNullElse(this.enabled, "null"), requireNonNullElse(this.endDate, "null"));
    }
}
