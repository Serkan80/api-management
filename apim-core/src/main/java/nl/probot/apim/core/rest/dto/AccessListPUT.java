package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import static io.micrometer.common.util.StringUtils.isNotBlank;
import static java.util.Objects.requireNonNullElse;
import static nl.probot.apim.core.utils.IpUtility.isValidIPv4;
import static nl.probot.apim.core.utils.IpUtility.isValidIPv6;

public record AccessListPUT(

        @NotBlank
        String ip,
        String newIp,

        @Size(min = 1, max = 100)
        String description,
        Boolean blacklisted,
        Boolean whitelisted) {

    @JsonIgnore
    @AssertTrue(message = "At least one parameter must be provided")
    public boolean isNotEmpty() {
        return isNotBlank(this.newIp)
               || isNotBlank(this.description)
               || this.blacklisted != null
               || this.whitelisted != null;
    }

    @JsonIgnore
    @AssertTrue(message = "Blacklisted and whitelisted cannot have the same value")
    public boolean isValid() {
        if (this.blacklisted == null && this.whitelisted == null) {
            return true;
        }

        return requireNonNullElse(this.blacklisted, false) != requireNonNullElse(this.whitelisted, false);
    }


    @JsonIgnore
    @AssertTrue(message = "New ip is not a valid ip address")
    public boolean isValidIpAddress() {
        if (this.newIp == null || this.newIp.isBlank()) {
            return true;
        }

        return isValidIPv4(this.newIp) || isValidIPv6(this.newIp);
    }

    public Boolean toggle(Boolean type) {
        return Boolean.FALSE.equals(type);
    }
}
