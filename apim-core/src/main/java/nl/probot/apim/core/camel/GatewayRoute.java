package nl.probot.apim.core.camel;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import static jakarta.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static jakarta.ws.rs.core.MediaType.MULTIPART_FORM_DATA;
import static nl.probot.apim.core.camel.SubscriptionProcessor.SUBSCRIPTION_KEY;
import static nl.probot.apim.core.camel.SubscriptionProcessor.THROTTLING_ENABLED;
import static nl.probot.apim.core.camel.SubscriptionProcessor.THROTTLING_MAX_REQUESTS;
import static org.apache.camel.Exchange.CONTENT_TYPE;

@ApplicationScoped
public class GatewayRoute extends EndpointRouteBuilder {

    @ConfigProperty(name = "apim.context-root")
    String apimPath;

    @Inject
    SubscriptionProcessor subscriptionProcessor;

    @Inject
    MeterRegistry meterRegistry;

    @Override
    public void configure() {
        onException(Throwable.class)
                .handled(true)
                .process(exchange -> CamelUtils.metrics(exchange, this.meterRegistry, false))
                .process(CamelUtils::cleanUpHeaders)
                .process(CamelUtils::setErrorMessage)
                .end();

        //@formatter:off
        from(platformHttp(this.apimPath).matchOnUriPrefix(true))
                .id("apimRoute")
                .process(exchange -> CamelUtils.metrics(exchange, this.meterRegistry, true))
                .process(this.subscriptionProcessor)
                .choice()
                    .when(exchangeProperty(THROTTLING_ENABLED).isEqualTo(true))
                        .to("direct:throttling")
                .end()
                .choice()
                    .when(header(CONTENT_TYPE).contains(MULTIPART_FORM_DATA))
                        .process(CamelUtils::multiPartProcessor)
                    .endChoice()
                    .when(header(CONTENT_TYPE).isEqualTo(APPLICATION_FORM_URLENCODED))
                        .process(CamelUtils::multiPartProcessor)
                    .endChoice()
                .end()
                .process(CamelUtils::forwardUrlProcessor)
                .process(CamelUtils::cleanUpHeaders)
                .toD("${exchangeProperty.forwardUrl}?bridgeEndpoint=true&skipRequestHeaders=false&followRedirects=true&connectionClose=true&copyHeaders=true${exchangeProperty.clientAuth}")
                .process(exchange -> CamelUtils.metrics(exchange, this.meterRegistry, false));
        //@formatter:on

        from("direct:throttling")
                .throttle(exchangeProperty(THROTTLING_MAX_REQUESTS), header(SUBSCRIPTION_KEY))
                .totalRequestsMode()
                .rejectExecution(true)
                .timePeriodMillis(60000);
    }
}

