package nl.probot.apim.core;

import io.quarkus.logging.Log;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

import java.util.Map;

@Path("/mock/auth")
public class MockWebServer {

    @POST
    public Response token(@Context RoutingContext ctx) {
        var request = ctx.request();

        Log.debugf("path: %s, params: %s, query: %s\n", request.path(), request.params(), request.query());
        request.headers().forEach((k, v) -> Log.debugf("headers: %s, %s\n", k, v));
        request.formAttributes().forEach((k, v) -> Log.debugf("form data: %s, %s\n", k, v));

        return Response.ok(Map.of("access_token", "123456", "token_type", "Bearer", "expires_in", "3600")).build();
    }
}
