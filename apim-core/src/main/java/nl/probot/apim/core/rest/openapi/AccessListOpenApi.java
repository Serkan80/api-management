package nl.probot.apim.core.rest.openapi;

import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.rest.dto.AccessList;
import nl.probot.apim.core.rest.dto.AccessListPOST;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;
import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;

@Path("/access-list")
@Tag(name = "AccessList Controller")
@SecurityRequirement(name = "basic")
@SecuritySchemes(@SecurityScheme(type = HTTP, scheme = "basic", securitySchemeName = "basic"))
public interface AccessListOpenApi {

    @POST
    @Operation(summary = "Adds a new AccessList and returns its Location")
    @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri")))
    RestResponse<Void> save(@Valid AccessListPOST dto, UriInfo uriInfo);

    @PUT
    @Operation(summary = "Updates the given AccessList")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not updated", responseCode = "204", description = "When the access-list is not updated"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the access-list is not found")
    })
    RestResponse<Void> update(@Valid AccessListPUT dto);

    @GET
    @Operation(summary = "Returns all access-lists")
    List<AccessList> findAll();
}
