package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import nl.probot.apim.core.entities.AuthenticationType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.hibernate.validator.constraints.URL;

import static io.micrometer.common.util.StringUtils.isNotBlank;

public record ApiUPDATE(

        @Size(max = 100)
        @Schema(example = "/jp")
        String proxyPath,

        @URL
        @Schema(example = "https://jsonplaceholder.typicode.com")
        String proxyUrl,

        @Size(max = 100)
        @Schema(example = "sek")
        String owner,

        @URL
        @Schema(example = "http://localhost:8080/q/swagger-ui")
        String openApiUrl,

        @Size(max = 200)
        @Schema(example = "test description")
        String description,

        @Min(1)
        @Max(1_000_000)
        @Schema(description = "max amount of requests per minute")
        Integer maxRequests,
        AuthenticationType authenticationType
) {

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotEmpty() {
        return isNotBlank(this.proxyPath)
               || isNotBlank(this.proxyUrl)
               || isNotBlank(this.owner)
               || this.description != null
               || this.openApiUrl != null
               || this.maxRequests != null
               || this.authenticationType != null;
    }
}
