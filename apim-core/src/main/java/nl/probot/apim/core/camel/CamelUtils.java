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
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.NewCookie;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.HttpHeaders.COOKIE;
import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
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

    public static final String TRACE_ID = "X-APIM-TRACE-ID";

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
                if (exchange.getIn().getHeader(CONTENT_TYPE).equals(APPLICATION_FORM_URLENCODED)) {
                    multiPartBuilder.setContentType(ContentType.APPLICATION_FORM_URLENCODED);
                    multiPartBuilder.addParameter(new BasicNameValuePair(entry.getKey(), entry.getValue().toString()));
                } else {
                    multiPartBuilder.addTextBody(entry.getKey(), entry.getValue().toString());
                }
                exchange.getIn().getHeaders().remove(entry.getKey());
            });
        }

        // binary part of multipart
        if (attachments != null) {
            attachments.entrySet()
                    .forEach(entry -> multiPartBuilder.addBinaryBody(entry.getKey(), Unchecked.supplier(() -> entry.getValue().getInputStream()).get()));
        }

        exchange.getMessage().setBody(multiPartBuilder.build());
    }

    public static void forwardUrlProcessor(Exchange exchange) {
        var incomingRequestPath = exchange.getIn().getHeader(HTTP_URI, String.class);
        var subscription = exchange.getProperty(SUBSCRIPTION, SubscriptionEntity.class);
        var api = subscription.findApi(incomingRequestPath);
        var forwardUrl = api.proxyUrl + incomingRequestPath.substring(incomingRequestPath.indexOf('/', 1)).replace(api.proxyPath, "");

        Log.debugf("forward url: %s", forwardUrl);
        exchange.setProperty("forwardUrl", forwardUrl);
        exchange.getIn().setHeader("X-Forward-For", exchange.getIn().getHeader(REMOTE_ADDRESS));
        exchange.getIn().setHeader(TRACE_ID, UUID.randomUUID());
    }

    public static void cleanUpHeaders(Exchange exchange) {
        exchange.getIn().removeHeader(HTTP_URI);
        exchange.getIn().removeHeader(HTTP_PATH);
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
        message.setHeader(Exchange.CONTENT_TYPE, APPLICATION_JSON);
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

    public static void passthroughAuth(Exchange exchange, String accessTokenName) {
        var in = exchange.getIn();
        var authorization = in.getHeader(AUTHORIZATION, String.class);

        if (authorization == null || authorization.isBlank()) {
            var cookies = parseCookies(in.getHeader(COOKIE, String.class));
            cookies.stream()
                    .filter(cookie -> cookie.getName().startsWith("q_session") || cookie.getName().equals(accessTokenName))
                    .map(Cookie::getValue)
                    .findFirst()
                    .ifPresent(at -> in.setHeader(AUTHORIZATION, "Bearer %s".formatted(at)));
        }
    }

    public static void metrics(Exchange exchange, MeterRegistry registry, boolean start) {
        if (start) {
            var sample = Timer.start(registry);
            exchange.setProperty("timer", sample);
            exchange.setProperty("httpPath", sanitize(exchange.getIn().getHeader(HTTP_URI, String.class)));
        } else {
            var timer = exchange.getProperty("timer", Sample.class);
            var subName = Optional.ofNullable(exchange.getProperty(SUBSCRIPTION, SubscriptionEntity.class))
                    .map(entity -> entity.name)
                    .orElse("error: no subscription found for this request");

            timer.stop(registry.timer(
                    "apim_metrics",
                    "status", requireNonBlankElse(exchange.getIn().getHeader(HTTP_RESPONSE_CODE, String.class), "500"),
                    "proxyPath", requireNonBlankElse(exchange.getProperty("proxyPath", String.class), "proxyPath not available"),
                    "httpPath", exchange.getProperty("httpPath", String.class),
                    "traceId", requireNonBlankElse(exchange.getIn().getHeader(TRACE_ID, String.class), ""),
                    "ts", OffsetDateTime.now().toString(),
                    "subscription", subName));
        }
    }

    private static List<NewCookie> parseCookies(String rawCookieHeader) {
        return Arrays.stream(requireNonNullElse(rawCookieHeader, "").split(";"))
                .map(cookiePair -> {
                    var parts = cookiePair.split("=", 2);
                    var name = parts[0].trim();
                    var value = parts.length > 1 ? parts[1].trim() : "";
                    return new NewCookie(name, value);
                })
                .toList();
    }

    private static String apiKeyValue(ApiCredentialEntity credential) {
        return switch (credential.apiKeyLocation) {
            case HEADER -> credential.apiKey;
            case QUERY -> "%s=%s".formatted(credential.apiKeyHeader, credential.apiKey).stripTrailing();
        };
    }

    private static String sanitize(String url) {
        var result = trimOptions(url);
        if (result.charAt(result.length() - 1) == '/') {
            result = result.substring(0, result.length() - 1);
        }
        return result;
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
}
