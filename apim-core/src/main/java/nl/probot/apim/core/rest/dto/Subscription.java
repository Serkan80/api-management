package nl.probot.apim.core.rest.dto;

import nl.probot.apim.core.entities.SubscriptionEntity;

import java.time.OffsetDateTime;

public record Subscription(
        String subscriptionKey,
        String subject,
        boolean enabled,
        OffsetDateTime createdAt
) {
    public static Subscription toDto(SubscriptionEntity entity) {
        return new Subscription(entity.subscriptionKey, entity.subject, entity.enabled, entity.createdAt);
    }
}
