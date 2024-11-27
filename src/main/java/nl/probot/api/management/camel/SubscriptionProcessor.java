package nl.probot.api.management.camel;

import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import nl.probot.api.management.entities.SubscriptionEntity;
import nl.probot.api.management.utils.CacheManager;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.function.Function;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static nl.probot.api.management.camel.CamelUtils.apiTokenAuth;
import static nl.probot.api.management.camel.CamelUtils.basicAuth;
import static nl.probot.api.management.camel.CamelUtils.clientCredentialsAuth;
import static nl.probot.api.management.camel.CamelUtils.extractProxyName;
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
        var subscription = this.cacheManager.get(subscriptionKey, () -> SubscriptionEntity.findByKey(subscriptionKey));
        var api = subscription.findApiBy(extractProxyName(incomingRequest).proxyName(), Function.identity());

        subscription.findApiCredential(api.id).ifPresent(credential -> {
            switch (api.authenticationType) {
                case BASIC -> basicAuth(exchange, credential.username, credential.password);
                case API_KEY -> apiTokenAuth(exchange, credential);
                case CLIENT_CREDENTIALS -> clientCredentialsAuth(exchange, credential);
                case NONE -> in.removeHeader(AUTHORIZATION);
            }
        });

        if (api.maxRequests != null) {
            exchange.setProperty(THROTTLING_ENABLED, true);
            exchange.setProperty(THROTTLING_MAX_REQUESTS, api.maxRequests);
        }

        in.setHeader(SUBSCRIPTION, subscription);
    }
}
