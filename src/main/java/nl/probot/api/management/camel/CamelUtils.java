package nl.probot.api.management.camel;

import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.UnauthorizedException;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.api.management.entities.ApiCredentialEntity;
import nl.probot.api.management.entities.SubscriptionEntity;
import org.apache.camel.Exchange;
import org.apache.camel.attachment.AttachmentMessage;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;

import java.util.Map;

import static io.quarkus.runtime.util.StringUtil.isNullOrEmpty;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.api.management.camel.SubscriptionProcessor.SUBSCRIPTION;
import static nl.probot.api.management.camel.SubscriptionProcessor.SUBSCRIPTION_KEY;
import static org.apache.camel.Exchange.EXCEPTION_CAUGHT;
import static org.apache.camel.Exchange.FAILURE_ENDPOINT;
import static org.apache.camel.Exchange.HTTP_PATH;
import static org.apache.camel.Exchange.HTTP_QUERY;
import static org.apache.camel.Exchange.HTTP_RESPONSE_CODE;
import static org.apache.camel.Exchange.HTTP_URI;
import static org.apache.camel.ExchangePropertyKey.FAILURE_ROUTE_ID;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants.AUTHENTICATED_USER;
import static org.apache.camel.component.platform.http.vertx.VertxPlatformHttpConstants.REMOTE_ADDRESS;

public final class CamelUtils {

    private CamelUtils() {
        super();
    }

    public static void validateUser(Exchange exchange) {
        var user = exchange.getIn().getHeader(AUTHENTICATED_USER, QuarkusHttpUser.class);
        if (user == null || user.principal().getString("username").isBlank()) {
            throw new AuthenticationFailedException("User not authenticated");
        }
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
        var extractedProxy = extractProxyName(incomingRequestPath);

        var proxyName = extractedProxy.proxyName;
        var proxyPath = incomingRequestPath.substring(extractedProxy.indexEnd);
        var subscription = exchange.getIn().getHeader(SUBSCRIPTION, SubscriptionEntity.class);
        var proxyUrl = subscription.findApiBy(proxyName, api -> api.proxyUrl);
        Log.debugf("proxyName: %s, proxyUrl: %s, proxyPath: %s\n", proxyName, proxyUrl, proxyPath);

        if (isNullOrEmpty(proxyUrl)) {
            throw new NotFoundException("No proxy found for request: %s".formatted(incomingRequestPath));
        }

        exchange.setProperty("forwardUrl", "%s%s".formatted(proxyUrl, proxyPath));
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

    public static void cleanHeaders(Exchange exchange) {
        exchange.getIn().removeHeader(HTTP_URI);
        exchange.getIn().removeHeader(HTTP_PATH);
        exchange.getIn().removeHeader(SUBSCRIPTION);
        exchange.getIn().removeHeader(SUBSCRIPTION_KEY);
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

    public static String requireNonBlankElse(String original, String orElse) {
        if (isNullOrEmpty(original) || "{}".equals(original)) {
            return orElse;
        }

        return original;
    }

    public static String trimOptions(String url) {
        if (url == null || url.isBlank()) {
            return "no url was available, probably due an error";
        }

        var optionsIndex = url.indexOf('?');
        if (optionsIndex == -1) {
            return url;
        }
        return url.substring(0, optionsIndex);
    }

    public static String apiKeyAuth(ApiCredentialEntity credential) {
        if (credential.apiKeyHeaderOutsideAuthorization) {
            return credential.apiKey;
        }

        return "%s %s".formatted(credential.apiKeyHeader, credential.apiKey);
    }

    public static void clientCredentialsAuth(Exchange exchange, ApiCredentialEntity credential) {
        var queryParams = exchange.getIn().getHeader(HTTP_QUERY, String.class);
        var firstChar = isNullOrEmpty(queryParams) ? '?' : '&';
        var oauthParams = "%soauth2ClientId=%s&oauth2ClientSecret=%s&oauth2TokenEndpoint=%s&oauth2Scope=%s"
                .formatted(firstChar, credential.clientId, credential.clientSecret, credential.clientUrl, credential.clientScope);
        exchange.getIn().setHeader(HTTP_QUERY, queryParams + oauthParams);
    }

    private record Result(String proxyName, int indexEnd) {
    }
}
