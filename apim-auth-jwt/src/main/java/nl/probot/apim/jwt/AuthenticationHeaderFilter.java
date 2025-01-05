package nl.probot.apim.jwt;

import io.vertx.core.http.HttpServerResponse;
import jakarta.ws.rs.container.ContainerResponseContext;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;

import static jakarta.ws.rs.core.HttpHeaders.WWW_AUTHENTICATE;

public class AuthenticationHeaderFilter {

    @ServerResponseFilter
    public void removeWwwAuthenticate(ContainerResponseContext ctx, HttpServerResponse res) {

        // prevents that a popup will show up on the frontend/dashboard
        // when wrong credentials are provided
        if (ctx.getStatus() == 401) {
            res.headers().remove(WWW_AUTHENTICATE);
        }
    }
}
