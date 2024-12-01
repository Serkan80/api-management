package nl.probot.apim.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.security.SecuritySchemes;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.util.Map;

import static org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType.HTTP;

@Path("/auth")
@Tag(name = "Authentication Controller")
@SecurityRequirement(name = "basic")
@SecuritySchemes(@SecurityScheme(type = HTTP, scheme = "basic", securitySchemeName = "basic"))
public interface AuthenticationOpenApi {

    @POST
    @Path("/token/web")
    @Operation(summary = "Generates access- & refresh token for web applications")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200", description = "A Http-Only Cookie with the access & refresh token"),
            @APIResponse(name = "Not Authenticated", responseCode = "401", description = "When authentication fails")
    })
    Response accessToken();

    @POST
    @Path("/token/bearer")
    @Operation(summary = "Generates an access token for backend applications")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200", description = "A bearer token"),
            @APIResponse(name = "Not Authenticated", responseCode = "401", description = "When authentication fails")
    })
    Map<String, Object> bearerToken();

    @POST
    @Path("/refresh")
    @SecurityRequirement(name = "none")
    @Operation(summary = "Intended for generating a new access token, when it is expired")
    @APIResponses({
            @APIResponse(name = "OK", responseCode = "200", description = "A Http-Only Cookie with a new access & refresh token"),
            @APIResponse(name = "Invalid", responseCode = "401", description = "When the given refresh token is invalid or expired")
    })
    Response refreshToken(@NotBlank String refreshToken);

    @GET
    @Path("/public-key")
    @Operation(summary = "Returns the public key to verify the bearer token")
    @APIResponse(name = "OK", responseCode = "200", description = "The public key")
    String publicKey() throws KeyStoreException, CertificateEncodingException;
}
