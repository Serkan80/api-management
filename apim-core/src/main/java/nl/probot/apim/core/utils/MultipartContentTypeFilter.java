package nl.probot.apim.core.utils;

import io.quarkus.vertx.web.RouteFilter;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;

@ApplicationScoped
public class MultipartContentTypeFilter {

    @ConfigProperty(name = "quarkus.http.body.multipart.file-content-types")
    List<String> allowedContentTypes;

    @RouteFilter(Priorities.USER + 1)
    public void filter(RoutingContext ctx) {
        if (!isMultipartRequest(ctx)) {
            ctx.next();
            return;
        }

        ctx.request().setExpectMultipart(true);
        var hasError = new AtomicBoolean(false);

        ctx.request().uploadHandler(fileUpload -> {
            var filePath = Path.of(fileUpload.filename());
            try {
                var contentType = Files.probeContentType(filePath);
                if (contentType == null || !allowedContentTypes.contains(contentType)) {
                    sendJsonError(ctx, 415, "Unsupported media type detected", contentType);
                    hasError.set(true);
                }
            } catch (IOException e) {
                sendJsonError(ctx, 500, "Error processing file", e.getMessage());
                hasError.set(true);
            }
        });

        if (!hasError.get()) {
            ctx.next();
        }
    }

    private boolean isMultipartRequest(RoutingContext ctx) {
        var contentType = ctx.request().getHeader(CONTENT_TYPE);
        return contentType != null && contentType.startsWith("multipart/");
    }

    private void sendJsonError(RoutingContext ctx, int statusCode, String message, String details) {
        ctx.response()
                .setStatusCode(statusCode)
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .end(JsonObject.of(
                        "error", message,
                        "details", details
                ).encodePrettily());
    }
}
