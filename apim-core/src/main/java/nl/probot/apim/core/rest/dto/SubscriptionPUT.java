package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static java.util.Objects.requireNonNullElse;

public record SubscriptionPUT(
        Boolean enabled,
        LocalDate endDate,

        @Size(max = 20)
        @JsonDeserialize(using = StringToArrayDeserializer.class)
        String[] accounts) {

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotEmpty() {
        return this.enabled != null || this.endDate != null || (this.accounts != null && this.accounts.length > 0);
    }

    @JsonIgnore
    @AssertTrue(message = "User Accounts must contain unique values")
    public boolean isAccountsUnique() {
        if (this.accounts != null && this.accounts.length > 0) {
            return Set.copyOf(List.of(this.accounts)).size() == this.accounts.length;
        }

        return true;
    }

    @Override
    public String toString() {
        return "enabled=%s, endDate=%s".formatted(requireNonNullElse(this.enabled, "null"), requireNonNullElse(this.endDate, "null"));
    }
}
