package nl.probot.apim.prometheus;

import io.smallrye.mutiny.Uni;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@Path("/")
@RegisterRestClient(configKey = "apim.prometheus")
public interface PrometheusClient {

    @GET
    @Path("/query")
    Uni<String> getQuery(@QueryParam("query") @NotBlank String query);
}
