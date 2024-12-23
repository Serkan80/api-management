package nl.probot.apim.core.camel;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.utils.CacheManager;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static nl.probot.apim.core.camel.CamelUtils.apiTokenAuth;
import static nl.probot.apim.core.camel.CamelUtils.basicAuth;
import static nl.probot.apim.core.camel.CamelUtils.clientCredentialsAuth;
import static nl.probot.apim.core.entities.AuthenticationType.PASSTHROUGH;
import static org.apache.camel.Exchange.HTTP_URI;

@Singleton
public class SubscriptionProcessor implements Processor {

    public static final String SUBSCRIPTION_KEY = "subscription-key";
    public static final String SUBSCRIPTION = "subscription";
    public static final String THROTTLING_ENABLED = "throttling_enabled";
    public static final String THROTTLING_MAX_REQUESTS = "throttling_maxRequests";

    @Inject
    CacheManager cacheManager;

    @Override
    @ActivateRequestContext
    public void process(Exchange exchange) {
        var in = exchange.getIn();
        var incomingRequest = in.getHeader(HTTP_URI, String.class);
        var subscriptionKey = in.getHeader(SUBSCRIPTION_KEY, String.class);
        var subscription = this.cacheManager.get(subscriptionKey, () -> SubscriptionEntity.findActiveByKey(subscriptionKey));
        var api = subscription.findApi(incomingRequest);

        checkApiCredentials(exchange, subscription, api);
        checkThrottling(exchange, api);
        exchange.setProperty(SUBSCRIPTION, subscription);
    }

    private static void checkApiCredentials(Exchange exchange, SubscriptionEntity subscription, ApiEntity api) {
        exchange.getIn().removeHeader(AUTHORIZATION);
        var authType = api.authenticationType;

        if (authType != null && authType != PASSTHROUGH) {
            var credential = subscription.findApiCredential(api.id).orElseThrow(() -> new WebApplicationException(
                    "Api requires %s authentication but no credentials were found for this Api".formatted(authType),
                    400));

            switch (authType) {
                case BASIC -> basicAuth(exchange, credential.username, credential.password);
                case API_KEY -> apiTokenAuth(exchange, credential);
                case CLIENT_CREDENTIALS -> clientCredentialsAuth(exchange, credential);
            }
        }
    }

    private static void checkThrottling(Exchange exchange, ApiEntity api) {
        if (api.maxRequests != null && api.maxRequests > 0) {
            exchange.setProperty(THROTTLING_ENABLED, true);
            exchange.setProperty(THROTTLING_MAX_REQUESTS, api.maxRequests);
        }
    }
}
