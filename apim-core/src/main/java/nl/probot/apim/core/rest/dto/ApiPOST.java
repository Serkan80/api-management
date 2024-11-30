package nl.probot.apim.core.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.AuthenticationType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.validator.constraints.URL;

public record ApiPOST(

        @NotBlank
        @Size(max = 100)
        @Schema(example = "/jp")
        String proxyPath,

        @URL
        @NotBlank
        @Schema(example = "https://jsonplaceholder.typicode.com")
        String proxyUrl,

        @NotBlank
        @Size(max = 100)
        @Schema(example = "sek")
        String owner,

        @URL
        @Schema(example = "http://localhost:8080/q/swagger-ui")
        String openApiUrl,

        @NotBlank
        @Size(max = 200)
        @Schema(example = "test description")
        String description,

        @Min(1)
        @Max(1_000_000)
        @Schema(description = "max amount of requests per minute")
        Integer maxRequests,

        AuthenticationType authenticationType,

        @Valid
        ApiCredential credential
) {
    public ApiEntity toEntity() {
        var result = new ApiEntity();
        result.proxyPath = this.proxyPath;
        result.proxyUrl = this.proxyUrl;
        result.owner = this.owner;
        result.openApiUrl = this.openApiUrl;
        result.description = this.description;
        result.maxRequests = this.maxRequests;
        result.authenticationType = this.authenticationType;
        return result;
    }
}
