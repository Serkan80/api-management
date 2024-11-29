package nl.probot.apim.auth.jpa.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;

import static io.smallrye.openapi.runtime.util.StringUtil.isNotEmpty;

public record UserPUT(@Email String email, String roles) {

    @JsonIgnore
    @AssertTrue(message = "At least one update parameter must be specified")
    public boolean isNotBlank() {
        return isNotEmpty(this.email) || isNotEmpty(this.roles);
    }
}
