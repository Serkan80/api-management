package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import nl.probot.apim.core.entities.SubscriptionEntity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record SubscriptionAll(
        String subscriptionKey,
        String name,
        boolean enabled,
        OffsetDateTime createdAt,
        LocalDate endDate,
        List<Api> apis,
        List<ApiCredential> credentials,
        String[] accounts
) {
//    @JsonGetter("accounts")
//    public String accountsAsString() {
//        return String.join(",", this.accounts);
//    }

    public static SubscriptionAll toDto(SubscriptionEntity entity) {
        var apis = entity.apis.stream().map(Api::toDto).toList();
        var credentials = entity.apiCredentials.stream().map(cr -> ApiCredential.toDto(entity.subscriptionKey, cr)).distinct().toList();

        return new SubscriptionAll(
                entity.subscriptionKey,
                entity.name,
                entity.enabled,
                entity.createdAt,
                entity.endDate,
                apis,
                credentials,
                entity.accounts);
    }
}
