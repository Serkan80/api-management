package nl.probot.apim.core.rest.dto;

import java.time.OffsetDateTime;

public record AccessList(
        String ip,
        String updatedBy,
        Boolean blacklisted,
        Boolean whitelisted,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String description
) {
}
