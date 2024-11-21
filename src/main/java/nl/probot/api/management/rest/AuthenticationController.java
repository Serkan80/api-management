package nl.probot.api.management.rest;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/auth")
@Authenticated
public class AuthenticationController {

    @Inject
    SecurityIdentity identity;

    @POST
    @Path("/token")
    public String generateToken() {
        return Jwt.upn(this.identity.getPrincipal().getName())
                .subject(this.identity.getPrincipal().getName())
                .groups(this.identity.getRoles())
                .sign();
    }
}
