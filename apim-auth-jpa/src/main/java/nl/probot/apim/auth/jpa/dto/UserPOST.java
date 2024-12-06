package nl.probot.apim.auth.jpa.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UserPOST(

        @NotBlank
        @Size(min = 8, max = 50)
        String username,

        @NotBlank
        @Size(min = 8, max = 50)
        String password,

        @NotBlank
        @Size(min = 8, max = 50)
        String passwordRepeat,

        @Email
        @NotBlank
        String email,

        @NotNull
        @Size(min = 1, max = 10)
        Set<String> roles
) {
    @JsonIgnore
    @AssertTrue(message = "Passwords should be the same")
    public boolean isPasswordSame() {
        return this.password.equals(this.passwordRepeat);
    }

    public String joinRoles() {
        return String.join(",", this.roles);
    }
}
