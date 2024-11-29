package nl.probot.apim.auth.jpa.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePassword(

        @NotBlank
        @Size(min = 8, max = 50)
        String username,

        @NotBlank
        @Size(min = 8, max = 50)
        String oldPassword,

        @NotBlank
        @Size(min = 8, max = 50)
        String newPassword,

        @NotBlank
        @Size(min = 8, max = 50)
        String newPasswordRepeat) {

    @JsonIgnore
    @AssertTrue(message = "New passwords should be the same")
    public boolean isPasswordSame() {
        return this.newPassword.equals(this.newPasswordRepeat);
    }
}
