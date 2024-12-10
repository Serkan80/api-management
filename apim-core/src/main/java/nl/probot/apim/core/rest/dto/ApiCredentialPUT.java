package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import nl.probot.apim.core.entities.ApiKeyLocation;
import org.hibernate.validator.constraints.URL;

import static io.micrometer.common.util.StringUtils.isNotBlank;

public record ApiCredentialPUT(
        @NotBlank
        String subscriptionKey,

        @NotNull
        Long apiId,
        String username,
        String password,
        String clientId,
        String clientSecret,

        @URL
        String clientUrl,
        String clientScope,
        String apiKey,
        String apiKeyHeader,
        ApiKeyLocation apiKeyLocation
) {
    @JsonIgnore
    @AssertTrue(message = "No credentials were provided")
    public boolean isCredentialsValid() {
        return isNotBlank(this.username)
               || isNotBlank(this.password)
               || isNotBlank(this.clientId)
               || isNotBlank(this.clientSecret)
               || isNotBlank(this.clientUrl)
               || isNotBlank(this.apiKey)
               || isNotBlank(this.apiKeyHeader)
               || this.apiKeyLocation != null;
    }
}
