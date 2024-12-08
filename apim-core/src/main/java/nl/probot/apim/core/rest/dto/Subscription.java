package nl.probot.apim.core.rest.dto;

import java.time.OffsetDateTime;

public record Subscription(
        String subscriptionKey,
        String name,
        boolean enabled,
        OffsetDateTime createdAt) {
}
