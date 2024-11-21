package nl.probot.api.management.rest.dto;

import nl.probot.api.management.entities.ApiCredentialEntity;
import nl.probot.api.management.entities.CompositeApiId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import static io.micrometer.common.util.StringUtils.isNotBlank;

public record ApiCredential(
        @Min(1) @NotNull
        Long subscriptionId,
        String username,
        String password,
        String clientId,
        String clientSecret,
        String clientUrl,
        String clientScope,
        String apiKey,
        String apiKeyHeader,
        Boolean apiKeyHeaderOutsideAuthorization
) {

    @JsonIgnore
    @AssertTrue(message = "no credentials were provided")
    public boolean credentialsArePresent() {
        return (isNotBlank(this.apiKey) && isNotBlank(this.apiKeyHeader) && this.apiKeyHeaderOutsideAuthorization != null)
               || (isNotBlank(this.clientId) && isNotBlank(this.clientSecret) && isNotBlank(this.clientUrl))
               || (isNotBlank(this.username) && isNotBlank(this.password));
    }

    public ApiCredentialEntity toEntity() {
        var result = new ApiCredentialEntity();
        result.id = new CompositeApiId();
        result.id.subscriptionId = this.subscriptionId;
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
