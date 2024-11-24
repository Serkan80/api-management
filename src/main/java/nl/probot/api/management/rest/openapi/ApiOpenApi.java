package nl.probot.api.management.rest.openapi;

import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.api.management.rest.dto.Api;
import nl.probot.api.management.rest.dto.ApiCredentialPOST;
import nl.probot.api.management.rest.dto.ApiPOST;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
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

    @POST
    @Path("/{apiId}/credentials")
    @Operation(summary = "Adds a credential to the given Api (and to the subscription via its key)")
    @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri")))
    RestResponse<Void> addCredential(@RestPath Long apiId, @Valid ApiCredentialPOST credential, @Context UriInfo uriInfo);

    @GET
    @Operation(summary = "Returns all Apis sorted by its owner")
    List<Api> findAll();
}
