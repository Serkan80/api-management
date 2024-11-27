package nl.probot.api.management.camel;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

import static nl.probot.api.management.camel.SubscriptionProcessor.SUBSCRIPTION_KEY;
import static nl.probot.api.management.camel.SubscriptionProcessor.THROTTLING_ENABLED;
import static nl.probot.api.management.camel.SubscriptionProcessor.THROTTLING_MAX_REQUESTS;
import static org.apache.camel.Exchange.CONTENT_TYPE;
import static org.apache.hc.core5.http.ContentType.MULTIPART_FORM_DATA;

@ApplicationScoped
public class GatewayRoute extends EndpointRouteBuilder {

    @Inject
    SubscriptionProcessor subscriptionProcessor;

    @Inject
    MeterRegistry meterRegistry;

    @Override
    public void configure() {
        onException(Throwable.class)
                .handled(true)
                .process(CamelUtils::cleanUpHeaders)
                .process(CamelUtils::setErrorMessage)
                .end();

        //@formatter:off
        from(platformHttp("/gateway").matchOnUriPrefix(true))
                .id("gatewayRoute")
                .process(exchange -> CamelUtils.timer(exchange, this.meterRegistry, true))
                .process(this.subscriptionProcessor)
                .choice()
                    .when(exchangeProperty(THROTTLING_ENABLED).isEqualTo(true))
                        .to("direct:throttling")
                .end()
                .choice()
                    .when(header(CONTENT_TYPE).contains(MULTIPART_FORM_DATA.getMimeType()))
                        .process(CamelUtils::multiPartProcessor)
                .end()
                .process(CamelUtils::forwardUrlProcessor)
                .process(CamelUtils::cleanUpHeaders)
                .to("http://ifconfig.me")
                .setHeader("X-Forward-For", body().convertToString())
                .toD("${exchangeProperty.forwardUrl}?bridgeEndpoint=true&skipRequestHeaders=false&followRedirects=true&connectionClose=true&copyHeaders=true${exchangeProperty.clientAuth}")
                .process(exchange -> CamelUtils.timer(exchange, this.meterRegistry, false));
        //@formatter:on

        from("direct:throttling")
                .throttle(exchangeProperty(THROTTLING_MAX_REQUESTS), header(SUBSCRIPTION_KEY))
                .totalRequestsMode()
                .rejectExecution(true)
                .timePeriodMillis(60000);
    }
}

