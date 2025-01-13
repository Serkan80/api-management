package nl.probot.apim.core.rest.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record Subscription(
        String name,
        String subscriptionKey,
        OffsetDateTime createdAt,
        LocalDate endDate,
        boolean enabled,
        String[] accounts) {
}
