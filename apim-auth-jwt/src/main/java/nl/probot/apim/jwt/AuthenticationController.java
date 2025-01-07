package nl.probot.apim.jwt;

import io.quarkus.logging.Log;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.NewCookie.SameSite;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.jwt.Claims;

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

    @ConfigProperty(name = "apim.cookie.domain.url", defaultValue = "localhost")
    String domainUrl;

    @ConfigProperty(name = "apim.cookie.site", defaultValue = "STRICT")
    String sameSite;

    @ConfigProperty(name = "apim.roles.manager")
    String managerRole;

    private KeyStore keystore;

    @PostConstruct
    public void init() {
        try {
            this.keystore = KeyUtils.loadKeyStore(this.keystoreLocation, this.keystorePassword, Optional.empty(), Optional.empty());
            Log.debugf("keystore loaded with %d entries", this.keystore.size());
        } catch (Exception e) {
            throw new WebApplicationException(e);
        }
    }

    @Override
    @Authenticated
    public Response accessToken() {
        var username = this.identity.getPrincipal().getName();
        var roles = this.identity.getRoles();

        return Response
                .ok(createUserInfo(username, roles))
                .cookie(cookie("access_token", generateAccessToken()), cookie("refresh_token", generateRefreshToken(username, roles)))
                .build();
    }

    @Override
    @Authenticated
    public Map<String, Object> bearerToken() {
        return Map.of(
                "access_token", generateAccessToken(),
                "expires_in", this.expirationAT,
                "token_type", "Bearer"
        );
    }

    @Override
    @PermitAll
    public Response refreshToken(String refreshToken) {
        try {
            var rt = this.jwtParser.verify(refreshToken, this.keystore.getCertificate("rt").getPublicKey());
            var username = (String) rt.getClaim(Claims.upn);
            var roles = rt.getGroups();

            return Response
                    .ok(createUserInfo(username, roles))
                    .cookie(cookie("access_token", generateAccessToken(username, roles)), cookie("refresh_token", generateRefreshToken(username, roles)))
                    .build();
        } catch (ParseException | GeneralSecurityException e) {
            throw new WebApplicationException("Invalid or expired refreshToken", e, 401);
        }
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
//                .secure(true)
                .sameSite(SameSite.valueOf(this.sameSite))
                .httpOnly(true)
                .domain(this.domainUrl)
                .path("/")
                .value(value)
                .build();
    }

    private String generateAccessToken(String username, Set<String> roles) {
        return Jwt.upn(username)
                .subject(username)
                .groups(roles)
                .sign();
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

    private String generateRefreshToken(String username, Set<String> roles) {
        try {
            var privateKey = (PrivateKey) this.keystore.getKey("rt", this.keystorePassword.toCharArray());
            return Jwt.upn(username)
                    .groups(roles)
                    .expiresAt(OffsetDateTime.now().plusDays(this.expirationDays).toInstant())
                    .sign(privateKey);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException e) {
            throw new WebApplicationException(e);
        }
    }

    private Map<String, Object> createUserInfo(String username, Set<String> roles) {
        return Map.of(
                "username", username,
                "roles", roles,
                "managerRole", this.managerRole
        );
    }
}