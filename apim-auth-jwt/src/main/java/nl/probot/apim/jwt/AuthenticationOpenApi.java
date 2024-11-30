package nl.probot.apim.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
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
    Response accessToken();

    @POST
    @Path("/token/refresh")
    Response refreshToken(@NotBlank String refreshToken);

    @POST
    @Path("/token/bearer")
    Map<String, Object> bearerToken();

    @GET
    @Path("/public-key")
    String publicKey() throws KeyStoreException, CertificateEncodingException;
}
