package nl.probot.api.management.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import nl.probot.api.management.entities.ApiCredentialEntity;
import nl.probot.api.management.entities.CompositeApiId;
import org.hibernate.validator.constraints.URL;

import static io.micrometer.common.util.StringUtils.isNotBlank;

public record ApiCredentialPOST(
        @NotBlank
        String subscriptionKey,
        String username,
        String password,
        String clientId,
        String clientSecret,

        @URL
        String clientUrl,
        String clientScope,
        String apiKey,
        String apiKeyHeader,
        Boolean apiKeyHeaderOutsideAuthorization
) {

    @JsonIgnore
    @AssertTrue(message = "No credentials were provided")
    public boolean isCredentialsValid() {
        return (isNotBlank(this.apiKey) && isNotBlank(this.apiKeyHeader) && this.apiKeyHeaderOutsideAuthorization != null)
               || (isNotBlank(this.clientId) && isNotBlank(this.clientSecret) && isNotBlank(this.clientUrl))
               || (isNotBlank(this.username) && isNotBlank(this.password));
    }

    public ApiCredentialEntity toEntity() {
        var result = new ApiCredentialEntity();
        result.id = new CompositeApiId();
        result.username = this.username;
        result.password = this.password;
        result.clientId = this.clientId;
        result.clientSecret = this.clientSecret;
        result.clientUrl = this.clientUrl;
        result.clientScope = this.clientScope;
        result.apiKey = this.apiKey;
        result.apiKeyHeader = this.apiKeyHeader;
        result.apiKeyHeaderOutsideAuthorization = this.apiKeyHeaderOutsideAuthorization;
        return result;
    }
}
