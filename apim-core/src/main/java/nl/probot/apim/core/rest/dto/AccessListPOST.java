package nl.probot.apim.core.rest.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import nl.probot.apim.core.entities.AccessListEntity;

import java.time.OffsetDateTime;

import static java.util.Objects.requireNonNullElse;
import static nl.probot.apim.core.utils.IpUtility.isValidIPv4;
import static nl.probot.apim.core.utils.IpUtility.isValidIPv6;

public record AccessListPOST(

        @NotBlank
        String ip,

        @Size(min = 1, max = 100)
        String description,
        Boolean blacklisted,
        Boolean whitelisted
) {

    @JsonIgnore
    @AssertTrue(message = "Blacklisted and whitelisted cannot have the same value")
    public boolean isValid() {
        return requireNonNullElse(this.blacklisted, false) ^ requireNonNullElse(this.whitelisted, false);
    }

    @JsonIgnore
    @AssertTrue(message = "Ip is not a valid ip address")
    public boolean isValidIpAddress() {
        return isValidIPv4(this.ip) || isValidIPv6(this.ip);
    }

    public AccessListEntity toEntity(String user) {
        var entity = new AccessListEntity();
        entity.ip = this.ip;
        entity.whitelisted = this.whitelisted;
        entity.blacklisted = this.blacklisted;
        entity.updatedBy = user;
        entity.description = this.description;
        entity.createdAt = OffsetDateTime.now();
        entity.isCidr = this.ip.contains("/");
        return entity;
    }
}