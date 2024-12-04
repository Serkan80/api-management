package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.ApiKeyLocation;
import nl.probot.apim.core.entities.CompositeApiId;
import org.hibernate.validator.constraints.URL;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static io.micrometer.common.util.StringUtils.isNotBlank;

@JsonInclude(NON_NULL)
public record ApiCredential(

        @NotBlank
        @JsonView(Views.AllFields.class)
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
        ApiKeyLocation apiKeyLocation
) {

    @JsonIgnore
    @AssertTrue(message = "No or incorrect credentials were provided")
    public boolean isCredentialsValid() {
        return (isNotBlank(this.apiKey) && isNotBlank(this.apiKeyHeader) && this.apiKeyLocation != null)
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
        result.apiKeyLocation = this.apiKeyLocation;
        return result;
    }

    public static ApiCredential toDto(String subKey, ApiCredentialEntity entity) {
        return new ApiCredential(
                subKey,
                entity.username,
                entity.password,
                entity.clientId,
                entity.clientSecret,
                entity.clientUrl,
                entity.clientScope,
                entity.apiKey,
                entity.apiKeyHeader,
                entity.apiKeyLocation
        );
    }
}
