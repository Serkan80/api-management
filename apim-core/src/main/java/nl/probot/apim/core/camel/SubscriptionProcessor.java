package nl.probot.apim.core.camel;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.utils.CacheManager;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static nl.probot.apim.core.camel.CamelUtils.apiTokenAuth;
import static nl.probot.apim.core.camel.CamelUtils.basicAuth;
import static nl.probot.apim.core.camel.CamelUtils.clientCredentialsAuth;
import static nl.probot.apim.core.camel.CamelUtils.metrics;
import static nl.probot.apim.core.camel.CamelUtils.passthroughAuth;
import static nl.probot.apim.core.entities.AuthenticationType.PASSTHROUGH;
import static org.apache.camel.Exchange.HTTP_URI;

@Singleton
public class SubscriptionProcessor implements Processor {

    public static final String PROXY_PATH = "proxyPath";
    public static final String SUBSCRIPTION_KEY = "subscription-key";
    public static final String SUBSCRIPTION = "subscription";
    public static final String THROTTLING_ENABLED = "throttling_enabled";
    public static final String THROTTLING_MAX_REQUESTS = "throttling_maxRequests";
    public static final String CACHING_KEY = "caching_key";

    @ConfigProperty(name = "mp.jwt.token.cookie", defaultValue = "NA")
    String accessTokenName;

    @Inject
    CacheManager cacheManager;

    @Inject
    MeterRegistry meterRegistry;

    @Override
    @ActivateRequestContext
    public void process(Exchange exchange) {
        var in = exchange.getIn();
        var incomingRequest = in.getHeader(HTTP_URI, String.class);
        var subscriptionKey = in.getHeader(SUBSCRIPTION_KEY, String.class);
        var subscription = this.cacheManager.getAndSet(subscriptionKey, () -> SubscriptionEntity.findActiveByKey(subscriptionKey));
        var api = subscription.findApi(incomingRequest);

        exchange.setProperty(SUBSCRIPTION, subscription);
        exchange.setProperty(PROXY_PATH, api.proxyPath);

        checkApiCredentials(exchange, subscription, api);
        checkThrottling(exchange, api);
        checkCaching(exchange, api, incomingRequest);
    }

    private void checkApiCredentials(Exchange exchange, SubscriptionEntity subscription, ApiEntity api) {
        var authType = api.authenticationType;

        if (authType != null && authType != PASSTHROUGH) {
            exchange.getIn().removeHeader(AUTHORIZATION);
            var credential = subscription.findApiCredential(api.id).orElseThrow(() -> new WebApplicationException(
                    "Api requires %s authentication but no credentials were found for this Api".formatted(authType),
                    400));

            switch (authType) {
                case BASIC -> basicAuth(exchange, credential.username, credential.password);
                case API_KEY -> apiTokenAuth(exchange, credential);
                case CLIENT_CREDENTIALS -> clientCredentialsAuth(exchange, credential);
            }
        } else {
            passthroughAuth(exchange, this.accessTokenName);
        }
    }

    private static void checkThrottling(Exchange exchange, ApiEntity api) {
        if (api.maxRequests != null && api.maxRequests > 0) {
            exchange.setProperty(THROTTLING_ENABLED, true);
            exchange.setProperty(THROTTLING_MAX_REQUESTS, api.maxRequests);
        }
    }

    private void checkCaching(Exchange exchange, ApiEntity api, String incomingRequest) {
        if (api.isPathCached(incomingRequest)) {
            var cacheKey = incomingRequest;

            this.cacheManager.get(cacheKey).ifPresentOrElse(timedValue -> {
                if (timedValue.isStale(api.cachingTTL * 1000)) {
                    this.cacheManager.invalidate(cacheKey);
                    Log.debugf("cache invalidated for key: %s", cacheKey);
                } else {
                    Log.debugf("Serving content from cache for key: %s", cacheKey);
                    exchange.getIn().setHeader("X-APIM-CACHE", incomingRequest);
                    exchange.getIn().setHeader("X-APIM-CACHE-TTL", api.cachingTTL);
                    exchange.getIn().setBody(timedValue.value());
                    metrics(exchange, this.meterRegistry, false, true);
                    exchange.setRouteStop(true);
                }
            }, () -> exchange.setProperty(CACHING_KEY, cacheKey));
        }
    }
}
