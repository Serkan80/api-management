package nl.probot.api.management.rest.dto;

import nl.probot.api.management.entities.ApiEntity;

public record Api(
        Long id,
        String proxyPath,
        String proxyUrl,
        String owner,
        String openApiUrl,
        String description,
        boolean enabled,
        Integer maxRequests
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
                entity.maxRequests
        );
    }
}
