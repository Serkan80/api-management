package nl.probot.api.management.rest;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.probot.api.management.rest.openapi.AuthenticationOpenApi;

import java.util.Map;

@Authenticated
@ApplicationScoped
public class AuthenticationController implements AuthenticationOpenApi {

    @Inject
    SecurityIdentity identity;

    @Override
    public Map<String, String> generateToken() {
        var accessToken = Jwt.upn(this.identity.getPrincipal().getName())
                .subject(this.identity.getPrincipal().getName())
                .groups(this.identity.getRoles())
                .sign();

        return Map.of(
                "access_token", accessToken
        );
    }
}
