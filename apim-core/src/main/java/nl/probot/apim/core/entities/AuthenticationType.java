package nl.probot.apim.core.entities;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AuthenticationType {
    BASIC,
    CLIENT_CREDENTIALS,
    API_KEY,
    PASSTHROUGH,
    NONE;

    @JsonCreator
    public static AuthenticationType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return PASSTHROUGH;
        }
        return AuthenticationType.valueOf(value);
    }
}
