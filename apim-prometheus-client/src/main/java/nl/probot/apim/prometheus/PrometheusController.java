package nl.probot.apim.prometheus;

import io.smallrye.mutiny.Multi;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.time.Duration;
import java.util.List;

import static jakarta.ws.rs.core.MediaType.TEXT_PLAIN;

@Path("/apim/prometheus")
public class PrometheusController {

    @Min(1000)
    @ConfigProperty(name = "apim.prometheus.poll.rate", defaultValue = "5000")
    int pollRate;

    @RestClient
    PrometheusClient client;

    @GET
    @Path("/metrics")
    @RestStreamElementType(TEXT_PLAIN)
    public Multi<String> getMetrics(@RestQuery @Size(min = 1, max = 10) List<String> query) {
        return Multi.createFrom()
                .ticks().every(Duration.ofMillis(this.pollRate))
                .flatMap(tick -> Multi.createFrom().iterable(query))
                .onItem().transformToUniAndMerge(q -> this.client.getQuery(q));
    }
}
