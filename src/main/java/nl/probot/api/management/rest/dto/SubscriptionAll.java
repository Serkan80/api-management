package nl.probot.api.management.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import nl.probot.api.management.entities.SubscriptionEntity;

import java.time.OffsetDateTime;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record SubscriptionAll(
        String subscriptionKey,
        String subject,
        boolean enabled,
        OffsetDateTime createdAt,
        List<ApiCredential> credentials,
        List<Api> apis
) {
    public static SubscriptionAll toDto(SubscriptionEntity entity) {
        var apis = entity.apis.stream().map(Api::toDto).toList();
        var credentials = entity.apiCredentials.stream().map(cr -> ApiCredential.toDto(entity.subscriptionKey, cr)).toList();

        return new SubscriptionAll(
                entity.subscriptionKey,
                entity.subject,
                entity.enabled,
                entity.createdAt,
                credentials,
                apis);
    }
}
