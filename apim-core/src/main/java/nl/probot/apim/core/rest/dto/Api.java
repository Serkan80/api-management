package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.AuthenticationType;

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
        Boolean cachingEnabled,
        Integer cachingTTL,
        String cachedPaths,
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
                entity.cachingEnabled,
                entity.cachingTTL,
                entity.cachedPaths,
                entity.maxRequests,
                entity.authenticationType
        );
    }
}
