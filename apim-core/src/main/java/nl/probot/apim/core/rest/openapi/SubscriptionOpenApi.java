package nl.probot.apim.core.rest.openapi;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiCredentialPUT;
import nl.probot.apim.core.rest.dto.Subscription;
import nl.probot.apim.core.rest.dto.SubscriptionAll;
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import nl.probot.apim.core.rest.dto.SubscriptionPUT;
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
import java.util.Map;
import java.util.Set;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;
import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;

@Path("/apim/core/subscriptions")
@Tag(name = "Subscription Controller")
@SecurityRequirement(name = "basic")
@SecuritySchemes(@SecurityScheme(type = HTTP, scheme = "basic", securitySchemeName = "basic"))
public interface SubscriptionOpenApi {

    @POST
    @Operation(summary = "Adds a new Subscription and returns its key via the Location Header")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri"))),
            @APIResponse(name = "Not updated", responseCode = "400", description = "When the subscription contains existing accounts")
    })
    RestResponse<Map<String, String>> save(@Valid SubscriptionPOST sub, @Context UriInfo uriInfo);

    @PUT
    @Path("/{key}")
    @Operation(summary = "Updates the given Subscription")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not updated", responseCode = "204", description = "When the subscription is not updated"),
            @APIResponse(name = "Not updated", responseCode = "400", description = "When the subscription contains existing accounts")
    })
    RestResponse<Void> update(@RestPath String key, @Valid SubscriptionPUT sub);

    @GET
    @Operation(summary = "Returns all Subscriptions without their Api's")
    List<Subscription> findAll();

    @GET
    @Path("/{key}")
    @Operation(summary = "Returns the Subscription and its Api's for the given key")
    @APIResponses(value = {
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "Not Found")
    })
    SubscriptionAll findByKey(@RestPath String key);

    @GET
    @Path("/accounts")
    @Operation(summary = "Returns the Subscription and its Api's for the current logged in user")
    SubscriptionAll findByAccount(@Context SecurityIdentity identity);

    @GET
    @Path("/search")
    List<Subscription> search(@RestQuery("q") String searchQuery);

    @POST
    @Path("/{key}/apis")
    @Operation(summary = "Adds the Apis for the given Subscription")
    @APIResponses(value = {
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Apis Not Found", responseCode = "204", description = "When the given Api's are not found"),
            @APIResponse(name = "Subscription Not Found", responseCode = "404", description = "When the given Subscription is not found")
    })
    RestResponse<SubscriptionAll> addApi(@RestPath String key, @NotEmpty Set<Long> apiIds);

    @POST
    @Path("/credentials")
    @Operation(summary = "Adds a credential to the given Api")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the subscription or api is not found")
    })
    RestResponse<Void> addCredential(@Valid ApiCredential credential);

    @PUT
    @Path("/credentials")
    @Operation(summary = "Updates the given credential")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "Not Updated", responseCode = "204", description = "When the credential is not updated"),
            @APIResponse(name = "Not Found", responseCode = "404", description = "When the subscription is not found")
    })
    RestResponse<Void> updateCredential(@Valid ApiCredentialPUT credential);

    @DELETE
    @Path("/cleanup")
    @Operation(summary = "Cleans up expired subscriptions")
    @APIResponse(name = "OK", responseCode = "200", description = "The total counts of deleted subscriptions & credentials")
    RestResponse<Map<String, Long>> cleanupExpiredSubscriptions();
}
