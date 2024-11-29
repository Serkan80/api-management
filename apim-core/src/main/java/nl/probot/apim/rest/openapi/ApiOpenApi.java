package nl.probot.apim.rest.openapi;

import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.rest.dto.Api;
import nl.probot.apim.rest.dto.ApiCredential;
import nl.probot.apim.rest.dto.ApiPOST;
import nl.probot.apim.rest.dto.ApiUPDATE;
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
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;
import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;

@Path("/apis")
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
    RestResponse<Void> update(@RestPath Long apiId, @Valid ApiUPDATE api);

    @GET
    @Operation(summary = "Returns all Apis sorted by its owner")
    List<Api> findAll();

    @POST
    @Path("/{apiId}/credentials")
    @Operation(summary = "Adds a credential to the given Api")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri"))),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the subscription or api is not found")
    })
    void addCredential(@RestPath Long apiId, @Valid ApiCredential credential);

    @PUT
    @Path("/{apiId}/credentials")
    @Operation(summary = "Updates the given credential")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not Updated", responseCode = "204", description = "When the credential is not updated"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the subscription is not found")
    })
    RestResponse<Void> updateCredential(Long apiId, @Valid ApiCredential credential);
}
