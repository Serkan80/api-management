package nl.probot.api.management.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import nl.probot.api.management.entities.ApiEntity;
import nl.probot.api.management.entities.AuthenticationType;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record Api(
        Long id,
        String proxyPath,
        String proxyUrl,
        String owner,
        String openApiUrl,
        String description,
        boolean enabled,
        Integer maxRequests,
        AuthenticationType authenticationType
) {
    public static Api toDto(ApiEntity entity) {
        return new Api(
                entity.id,
                entity.proxyPath,
                entity.proxyUrl,
                entity.owner,
                entity.openApiUrl,
                entity.description,
                entity.enabled,
                entity.maxRequests,
                entity.authenticationType
        );
    }
}
