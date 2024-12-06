package nl.probot.apim.auth.jpa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import nl.probot.apim.auth.jpa.dto.ChangePassword;
import nl.probot.apim.auth.jpa.dto.User;
import nl.probot.apim.auth.jpa.dto.UserPOST;
import nl.probot.apim.auth.jpa.dto.UserPUT;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.headers.Header;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import java.util.List;
import java.util.UUID;

import static org.eclipse.microprofile.openapi.annotations.enums.SchemaType.STRING;

@Path("/apim/users")
@Tag(name = "User Controller")
public interface UserOpenApi {

    @POST
    @Operation(summary = "Adds a new User and returns its Location")
    @APIResponse(name = "OK", responseCode = "201", headers = @Header(name = "Location", schema = @Schema(type = STRING, format = "uri")))
    RestResponse<Void> save(@Valid UserPOST user);

    @PUT
    @Path("/password")
    @Operation(summary = "Changes the password of the user")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200"),
            @APIResponse(name = "OK", responseCode = "400", description = "When the user is blocked or when the old password doesn't match"),
            @APIResponse(name = "OK", responseCode = "404", description = "When the user is not found")
    })
    void changePassword(@Valid ChangePassword request);

    @PUT
    @Path("/{id}")
    RestResponse<Void> update(@RestPath UUID id, @Valid UserPUT user);

    @POST
    @Path("/{id}/activate")
    void activate(@RestPath UUID id, @RestQuery @NotNull Boolean enable);

    @GET
    List<User> findAll();
}
