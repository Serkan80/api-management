package nl.probot.apim.core.rest.openapi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.ApiPUT;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;
import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;

@Path("/apim/core/apis")
@Tag(name = "Api Controller")
@SecurityRequirement(name = "basic")
@SecuritySchemes(@SecurityScheme(type = HTTP, scheme = "basic", securitySchemeName = "basic"))
public interface ApiOpenApi {

    @POST
    @Operation(summary = "Adds a new Api and returns its Location")
    @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri")))
    RestResponse<Void> save(@Valid ApiPOST api, @Context UriInfo uriInfo);

    @PUT
    @Path("/{apiId}")
    @Operation(summary = "Updates the given Api")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not updated", responseCode = "204", description = "When the api is not updated"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the subscription is not found")
    })
    RestResponse<Void> update(@RestPath Long apiId, @Valid ApiPUT api);

    @GET
    @Operation(summary = "Returns all Apis sorted by its owner")
    List<Api> findAll();

    @GET
    @Path("/search")
    @Operation(summary = "Searches Apis for the given search query")
    List<Api> search(@RestQuery("q") @Size(max = 10) String searchQuery);

    @GET
    @Path("/{id}")
    @Operation(summary = "Returns the Api for the given id")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "Not found")
    })
    Api findById(@RestPath Long id);
}
