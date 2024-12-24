package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonGetter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record Subscription(
        String name,
        String subscriptionKey,
        OffsetDateTime createdAt,
        LocalDate endDate,
        boolean enabled,
        String[] accounts) {

    @JsonGetter("accounts")
    public String accountsAsString() {
        return String.join(",", this.accounts);
    }
}
