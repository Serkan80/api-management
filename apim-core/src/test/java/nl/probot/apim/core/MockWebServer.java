package nl.probot.apim.core;

import io.quarkus.logging.Log;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.RouteBase;
import io.quarkus.vertx.web.RoutingExchange;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import static io.quarkus.vertx.web.Route.HttpMethod.POST;
import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.requireNonNullElse;
import static org.junit.platform.commons.util.StringUtils.isNotBlank;

@ApplicationScoped
@RouteBase(path = "/mock", produces = APPLICATION_JSON)
public class MockWebServer {

    /**
     * Mocked client credentials authentication endpoint.
     *
     * @param exchange
     */
    @Route(path = "/auth", methods = POST, consumes = APPLICATION_FORM_URLENCODED)
    public void token(RoutingExchange exchange) {
        var request = exchange.request();
        Log.debugf("path: %s, params: %s, query: %s", request.path(), request.params(), request.query());

        if ("client_credentials".equals(request.getFormAttribute("grant_type"))) {
            exchange.response().end(JsonObject.of("access_token", "123456", "token_type", "Bearer", "expires_in", "3600").encode());
        } else {
            exchange.response().setStatusCode(401).end();
        }
    }

    /**
     * Catches all http requests, except the ones defined in the regex.
     *
     * Returns the method, url & the headers in the response body.
     */
    @Route(regex = "/(?!(apim/core|gateway|mock/auth|multipart)).*")
    public void methods(RoutingContext context) {
        var request = context.request();
        Log.debugf("method: %s, path: %s, query: %s", request.method(), request.path(), request.query());

        var response = context.response();
        var headers = new JsonObject();
        request.headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));
        Log.debugf("headers: \n%s", headers.encodePrettily());

        var query = requireNonNullElse(request.query(), "");
        var querySeparator = isNotBlank(query) ? "?" : "";

        var body = JsonObject.of(
                "method", request.method().name(),
                "url", "%s%s%s".formatted(request.path(), querySeparator, query),
                "headers", headers
        );

        response.putHeader(CONTENT_TYPE, APPLICATION_JSON).end(body.encode());
    }
}
