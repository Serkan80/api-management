package nl.probot.apim.rest;

import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import nl.probot.apim.rest.openapi.AuthenticationOpenApi;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.UnrecoverableKeyException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.smallrye.jwt.util.KeyUtils.loadKeyStore;
import static jakarta.ws.rs.core.NewCookie.SameSite.STRICT;

@ApplicationScoped
public class AuthenticationController implements AuthenticationOpenApi {

    @Inject
    SecurityIdentity identity;

    @Inject
    JWTParser jwtParser;

    @Min(1)
    @ConfigProperty(name = "rt.expiration.days", defaultValue = "7")
    int expirationDays;

    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan")
    int expirationAT;

    @ConfigProperty(name = "mp.jwt.verify.publickey.location")
    String keystoreLocation;

    @ConfigProperty(name = "smallrye.jwt.keystore.password")
    String keystorePassword;

    private KeyStore keystore;

    @PostConstruct
    public void init() {
        try {
            this.keystore = loadKeyStore(this.keystoreLocation, this.keystorePassword, Optional.empty(), Optional.empty());
            Log.debugf("keystore loaded with %d entries", this.keystore.size());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    @Authenticated
    public Response accessToken() {
        var username = this.identity.getPrincipal().getName();
        var email = this.identity.getAttribute("email").toString();
        var roles = this.identity.getRoles();

        return Response
                .ok(createUserInfo(username, email, roles))
                .cookie(cookie("access_token", generateAccessToken()), cookie("refresh_token", generateRefreshToken(username, email, roles)))
                .build();
    }

    @Override
    @PermitAll
    public Response refreshToken(@NotBlank String refreshToken) {
        try {
            var rt = this.jwtParser.verify(refreshToken, this.keystore.getCertificate("rt").getPublicKey());
            var username = rt.getClaim("upn").toString();
            var email = rt.getClaim("email").toString();
            var roles = (Set) rt.getClaim("groups");

            return Response
                    .ok(createUserInfo(username, email, roles))
                    .cookie(cookie("access_token", generateAccessToken()), cookie("refresh_token", generateRefreshToken(username, email, roles)))
                    .build();
        } catch (ParseException | GeneralSecurityException e) {
            throw new WebApplicationException("Invalid or expired refreshToken", e, 401);
        }
    }

    @Override
    @Authenticated
    public Map<String, Object> bearerToken() {
        return Map.of(
                "access_token", generateAccessToken(),
                "expires_in", this.expirationAT,
                "type", "bearer"
        );
    }

    @Override
    @PermitAll
    public String publicKey() throws KeyStoreException {
        return """
                -----BEGIN PUBLIC KEY-----
                %s
                -----END PUBLIC KEY-----
                """
                .formatted(Base64.getMimeEncoder().encodeToString(
                        this.keystore.getCertificate("rt").getPublicKey().getEncoded()
                ));
    }

    private NewCookie cookie(String name, String value) {
        return new NewCookie.Builder(name)
//              .secure(true)
                .sameSite(STRICT)
                .httpOnly(true)
                .path("/")
                .value(value)
                .build();
    }

    /*
     * The keypair, issuer, audience & exp. time, are all configured in application.properties.
     */
    private String generateAccessToken() {
        return Jwt.upn(this.identity.getPrincipal().getName())
                .subject(this.identity.getPrincipal().getName())
                .groups(this.identity.getRoles())
                .sign();
    }

    private String generateRefreshToken(String username, String email, Set<String> roles) {
        try {
            var privateKey = (PrivateKey) this.keystore.getKey("rt", this.keystorePassword.toCharArray());
            return Jwt.upn(username)
                    .groups(roles)
                    .claim("email", email)
                    .expiresAt(OffsetDateTime.now().plusDays(this.expirationDays).toInstant())
                    .sign(privateKey);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new WebApplicationException(e);
        }
    }

    private Map<String, Object> createUserInfo(String username, String email, Set<String> roles) {
        return Map.of(
                "username", username,
                "roles", roles,
                "email", email);
    }
}