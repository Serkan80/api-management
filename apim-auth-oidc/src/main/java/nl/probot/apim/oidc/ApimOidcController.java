package nl.probot.apim.oidc;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.util.Map;

@Authenticated
@Path("/apim/oidc")
public class ApimOidcController {

    @ConfigProperty(name = "apim.roles.manager")
    String managerRole;

    @Inject
    SecurityIdentity identity;

    @GET
    @Path("/redirect")
    public Response getRedirectPath() {
        return Response
                .seeOther(URI.create("/pages/index.html"))
                .build();
    }

    @GET
    @Path("/userinfo")
    public Response userInfo() {
        return Response
                .ok(Map.of(
                        "username", this.identity.getPrincipal().getName(),
                        "roles", this.identity.getRoles(),
                        "managerRole", this.managerRole
                ))
                .build();
    }
}
