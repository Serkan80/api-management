package nl.probot.apim.core.rest;

import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import jakarta.ws.rs.WebApplicationException;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.Map;
import java.util.Objects;

import static org.jboss.resteasy.reactive.RestResponse.Status.BAD_REQUEST;
import static org.jboss.resteasy.reactive.RestResponse.Status.INTERNAL_SERVER_ERROR;
import static org.jboss.resteasy.reactive.RestResponse.Status.UNAUTHORIZED;

public class ExceptionMapper {

    @ServerExceptionMapper(priority = 1)
    public RestResponse<Map<String, String>> toResponse(WebApplicationException e) {
        var message = getMessage(e);
        Log.errorf(message);
        var status = Objects.requireNonNullElse(e.getResponse().getStatusInfo(), BAD_REQUEST);

        return RestResponse.status(status, Map.of(
                "message", message,
                "exception", e.getClass().getName()
        ));
    }

    @ServerExceptionMapper(priority = 1)
    public RestResponse<Map<String, String>> toResponse(Exception e) {
        var message = getMessage(e);
        Log.errorf(message);
        return RestResponse.status(INTERNAL_SERVER_ERROR, Map.of(
                "message", message,
                "exception", e.getClass().getName()
        ));
    }

    @ServerExceptionMapper
    public RestResponse<Map<String, String>> toResponse(AuthenticationFailedException e) {
        return RestResponse.status(UNAUTHORIZED, Map.of(
                "message", "Authentication failed",
                "exception", e.getClass().getName()
        ));
    }

    private String getMessage(Exception e) {
        var result = e.getMessage();
        var cause = e.getCause();
        var maxDepth = 3;
        while (cause != null && maxDepth-- > 0) {
            result = cause.getMessage();
            cause = cause.getCause();
        }
        return result;
    }
}
