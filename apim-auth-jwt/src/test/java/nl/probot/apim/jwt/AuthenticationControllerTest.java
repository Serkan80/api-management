package nl.probot.apim.jwt;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.ValidatableResponse;
import io.smallrye.jwt.auth.principal.JWTParser;
import io.smallrye.jwt.auth.principal.ParseException;
import jakarta.inject.Inject;
import org.eclipse.microprofile.jwt.Claims;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static io.restassured.matcher.RestAssuredMatchers.detailedCookie;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestHTTPEndpoint(AuthenticationController.class)
class AuthenticationControllerTest {

    @Inject
    JWTParser jwtParser;

    @Test
    @TestSecurity(user = "bob", roles = "admin", authMechanism = "basic")
    void checkWebLogin() throws ParseException {
        var response = webLogin()
                .body("username", is("bob"))
                .body("roles", hasSize(1))
                .body("roles", hasItem("admin"))
                .cookie("access_token", detailedCookie().sameSite("Strict").httpOnly(true).path("/").value(notNullValue()))
                .cookie("refresh_token", detailedCookie().sameSite("Strict").httpOnly(true).path("/").value(notNullValue()));

        var AT = this.jwtParser.parseOnly(response.extract().cookie("access_token"));
        var RT = this.jwtParser.parseOnly(response.extract().cookie("refresh_token"));

        assertThat(AT.getSubject()).isEqualTo("bob");
        assertThat(AT.getIssuer()).isEqualTo("apim-internal");
        assertThat(AT.getAudience()).containsExactly("apim-external");
        assertThat(AT.getGroups()).containsExactly("admin");

        assertThat((String) RT.getClaim(Claims.upn)).isEqualTo("bob");
        assertThat(RT.getIssuer()).isEqualTo("apim-internal");
        assertThat(RT.getAudience()).containsExactly("apim-external");
        assertThat(RT.getGroups()).containsExactly("admin");
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            rt_ok.txt,200
            rt_wrong.txt,401
            rt_expired.txt,401
            empty,400
            """)
    @TestSecurity(user = "bob", roles = "admin", authMechanism = "basic")
    void refreshToken(String file, int expectedStatus) throws IOException {
        String body;
        if (file.startsWith("rt_ok")) {
            body = webLogin().extract().cookie("refresh_token");
        } else if (file.equals("empty")) {
            body = "";
        } else {
            body = Files.readString(Path.of("src/test/resources/" + file));
        }

        var builder = getRefreshToken(body, expectedStatus);

        if (expectedStatus == 200) {
            builder
                    .log().all()
                    .body("username", is("bob"))
                    .body("roles", hasSize(1))
                    .body("roles", hasItem("admin"))
                    .cookie("access_token", detailedCookie().sameSite("Strict").httpOnly(true).path("/").value(notNullValue()))
                    .cookie("refresh_token", detailedCookie().sameSite("Strict").httpOnly(true).path("/").value(not(is(body))));
        }
    }

    @Test
    @TestSecurity(user = "bob", roles = "admin", authMechanism = "basic")
    void bearerToken() {
        bearerLogin()
                .body("access_token", notNullValue())
                .body("expires_in", is(3600))
                .body("token_type", is("Bearer"))
                .body("refresh_token", nullValue());
    }

    @Test
    void publicKey() {
        var publicKey = given().when().get("/public-key").then().extract().asString();

        assertThat(publicKey).isNotBlank().isEqualToIgnoringWhitespace("""
                                                                               -----BEGIN PUBLIC KEY-----
                                                                               MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAySoM3I2FA/BetdrS6lhJvTYKk6x71BSw
                                                                               QX1VDP3PR6c6rKmr0VAI/QijmlNY9nC9NLkBjVE8OUd6LVrNAEIG7DqXSXNOGDA1GFx68SLGNJ4O
                                                                               tz7Tt1T7lzeyu4ArSlT1uBnWroA+07xrtW5ILhbf1Uxyg0DL37Kf8KyC6x5Vaj4whR+pLip7ZoQG
                                                                               cOPPDeWlfDVnFj+YWtpVe6hzef5swJ5UIvr87DshSXrySxXKLow03VOyyL0Y4R06Q7ErARGmu19C
                                                                               pCerAJzzpDcz/J09DWfkrKmd7BCCEpJ2Wj55J9fhrTnrZPDldEqJ9JmV43tKZqYqKZpcOMfdhJug
                                                                               WBdzJQIDAQAB
                                                                               -----END PUBLIC KEY-----
                                                                               """
        );
    }

    private static ValidatableResponse webLogin() {
        return doRequest("/token/web", null, false, 200);
    }

    private static ValidatableResponse bearerLogin() {
        return doRequest("/token/bearer", null, false, 200);
    }

    private static ValidatableResponse getRefreshToken(Object body, int expectedStatus) {
        return doRequest("/refresh", body, true, expectedStatus);
    }

    private static ValidatableResponse doRequest(String path, Object body, boolean authDisabled, int expectedStatus) {
        var builder = given().contentType(APPLICATION_JSON);

        if (authDisabled) {
            builder.auth().none();
        }

        if (body != null) {
            builder.body(body);
        }

        return builder.when()
                .post(path)
                .then()
//                .log().all()
                .statusCode(expectedStatus);
    }
}