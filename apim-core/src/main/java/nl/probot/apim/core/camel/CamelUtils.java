package nl.probot.apim.core.camel;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;

import java.util.Map;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;
import static nl.probot.apim.core.camel.SubscriptionProcessor.SUBSCRIPTION;
import static nl.probot.apim.core.camel.SubscriptionProcessor.SUBSCRIPTION_KEY;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;
import static org.apache.camel.Exchange.FAILURE_ENDPOINT;
import static org.apache.camel.Exchange.HTTP_PATH;
import static org.apache.camel.Exchange.HTTP_QUERY;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.ExchangePropertyKey.FAILURE_ROUTE_ID;
import static org.apache.camel.component.http.HttpCredentialsHelper.generateBasicAuthHeader;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants.AUTHENTICATED_USER;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants.REMOTE_ADDRESS;

public final class CamelUtils {

    private CamelUtils() {
        super();
    }

    public static void multiPartProcessor(Exchange exchange) {
        var body = (Map<String, Object>) exchange.getIn().getBody(Map.class);
        var message = exchange.getIn(AttachmentMessage.class);
        var attachments = message.getAttachments();
        var multiPartBuilder = MultipartEntityBuilder.create();

        // text part of multipart
        if (body != null) {
            body.entrySet().forEach(entry -> {
                multiPartBuilder.addTextBody(entry.getKey(), entry.getValue().toString());
                exchange.getIn().getHeaders().remove(entry.getKey());
            });
        }

        // binary part of multipart
        if (attachments != null) {
            attachments.entrySet().forEach(entry -> multiPartBuilder.addBinaryBody(entry.getKey(), Unchecked.supplier(() -> entry.getValue().getInputStream()).get()));
        }

        exchange.getMessage().setBody(multiPartBuilder.build());
    }

    public static void forwardUrlProcessor(Exchange exchange) {
        var incomingRequestPath = exchange.getIn().getHeader(HTTP_URI, String.class);
        var subscription = exchange.getIn().getHeader(SUBSCRIPTION, SubscriptionEntity.class);
        var api = subscription.findApi(incomingRequestPath);
        var forwardUrl = api.proxyUrl + incomingRequestPath.substring(incomingRequestPath.indexOf('/', 1)).replace(api.proxyPath, "");

        Log.debugf("forward url: %s", forwardUrl);
        exchange.setProperty("forwardUrl", forwardUrl);
        exchange.getIn().setHeader("X-Forward-For", exchange.getIn().getHeader(REMOTE_ADDRESS));
    }

    public static Result extractProxyName(String incomingRequestPath) {
        var proxyIndexStart = incomingRequestPath.indexOf('/', 1);
        var proxyIndexEnd = incomingRequestPath.indexOf('/', proxyIndexStart + 1);

        if (proxyIndexEnd == -1) {
            proxyIndexEnd = incomingRequestPath.length();
        }
        return new Result(incomingRequestPath.substring(proxyIndexStart, proxyIndexEnd), proxyIndexEnd);
    }

    public static void cleanUpHeaders(Exchange exchange) {
        exchange.getIn().removeHeader(HTTP_URI);
        exchange.getIn().removeHeader(HTTP_PATH);
        exchange.getIn().removeHeader(SUBSCRIPTION);
        exchange.getIn().removeHeader(SUBSCRIPTION_KEY);
        exchange.getIn().removeHeader(AUTHENTICATED_USER);
    }

    public static void setErrorMessage(Exchange exchange) {
        var exception = exchange.getProperty(EXCEPTION_CAUGHT, Exception.class);
        var errorMsg = exception.getMessage();
        var status = 500;

        switch (exception) {
            case HttpOperationFailedException he -> {
                status = he.getStatusCode();
                errorMsg = he.getResponseBody();
            }
            case WebApplicationException we -> status = we.getResponse().getStatus();
            case AuthenticationFailedException ae -> status = 401;
            case UnauthorizedException ua -> status = 403;
            default -> status = 500;
        }

        var message = exchange.getMessage();
        message.setHeader(HTTP_RESPONSE_CODE, status);
        message.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        message.setBody(JsonObject.of(
                "routeId", exchange.getProperty(FAILURE_ROUTE_ID),
                "exception", exception.getClass(),
                "message", requireNonBlankElse(errorMsg, exception.getMessage()),
                "failureEndpoint", trimOptions(exchange.getProperty(FAILURE_ENDPOINT, String.class))
        ).encodePrettily());
        exchange.setRouteStop(true);
    }

    public static void basicAuth(Exchange exchange, String username, String password) {
        requireNonNull(username, "No Username was provided for Basic authentication");
        requireNonNull(password, "No Password was provided for Basic authentication");

        exchange.getIn().setHeader(AUTHORIZATION, generateBasicAuthHeader(username, password));
    }

    public static void apiTokenAuth(Exchange exchange, ApiCredentialEntity credential) {
        requireNonNull(credential.apiKey, "No ApiKey was provided for authentication");
        requireNonNull(credential.apiKeyLocation, "No ApiKeyLocation was provided for authentication");
        requireNonNull(credential.apiKeyHeader, "No ApiKeyHeader was provided for authentication");

        switch (credential.apiKeyLocation) {
            case QUERY -> exchange.getIn().setHeader(HTTP_QUERY, apiKeyValue(credential));
            case HEADER -> exchange.getIn().setHeader(credential.apiKeyHeader, apiKeyValue(credential));
        }
    }

    public static void clientCredentialsAuth(Exchange exchange, ApiCredentialEntity credential) {
        requireNonNull(credential.clientId, "No ClientId was provided for authentication");
        requireNonNull(credential.clientSecret, "No ClientSecret was provided for authentication");
        requireNonNull(credential.clientUrl, "No ClientUrl was provided for authentication");

        var oauthParams = "&oauth2ClientId=%s&oauth2ClientSecret=%s&oauth2TokenEndpoint=%s"
                .formatted(credential.clientId, credential.clientSecret, credential.clientUrl);
        oauthParams += credential.clientScope != null ? "&oauth2Scope=%s".formatted(credential.clientScope) : "";
        exchange.setProperty("clientAuth", oauthParams);
    }

    public static void timer(Exchange exchange, MeterRegistry registry, boolean start) {
        if (start) {
            var sample = Timer.start(registry);
            exchange.setProperty("timer", sample);
            exchange.setProperty(SUBSCRIPTION_KEY, exchange.getIn().getHeader(SUBSCRIPTION_KEY));
            exchange.setProperty("proxyPath", extractProxyName(exchange.getIn().getHeader(HTTP_URI, String.class)).proxyName());
        } else {
            var timer = exchange.getProperty("timer", Sample.class);
            timer.stop(registry.timer(
                    "apim_metrics",
                    "proxyPath", exchange.getProperty("proxyPath", String.class),
                    "subKey", exchange.getProperty(SUBSCRIPTION_KEY, String.class)));
        }
    }

    private static String apiKeyValue(ApiCredentialEntity credential) {
        return switch (credential.apiKeyLocation) {
            case HEADER -> credential.apiKey;
            case QUERY -> "%s=%s".formatted(credential.apiKeyHeader, credential.apiKey).stripTrailing();
        };
    }

    private static String trimOptions(String url) {
        if (url == null || url.isBlank()) {
            return "no url was available, probably due an error";
        }

        var optionsIndex = url.indexOf('?');
        if (optionsIndex == -1) {
            return url;
        }
        return url.substring(0, optionsIndex);
    }

    private static String requireNonBlankElse(String original, String orElse) {
        if (isNullOrEmpty(original) || "{}".equals(original)) {
            return orElse;
        }

        return original;
    }

    record Result(String proxyName, int indexEnd) {
    }
}
