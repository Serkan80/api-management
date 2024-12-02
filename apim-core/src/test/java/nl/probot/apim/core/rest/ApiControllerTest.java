package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.SubscriptionAll;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URL;
import java.time.temporal.ValueRange;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.entities.AuthenticationType.BASIC;
import static nl.probot.apim.core.entities.AuthenticationType.PASSTHROUGH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

@QuarkusTest
@TestHTTPEndpoint(ApiController.class)
class ApiControllerTest {

    @TestHTTPResource
    @TestHTTPEndpoint(SubscriptionController.class)
    URL subscriptionsUrl;

    @Test
    @TestSecurity(user = "bob", authMechanism = "basic")
    void save() {
        var request = Instancio.of(apiModel).create();
        var apiId = createApi(request);
        var api = getApiById(apiId);

        assertThat(api.id()).isEqualTo(Long.valueOf(apiId));
        assertThat(api.enabled()).isEqualTo(true);
        assertThat(api.proxyPath()).isEqualTo(request.proxyPath());
        assertThat(api.proxyUrl()).isEqualTo(request.proxyUrl());
        assertThat(api.owner()).isEqualTo(request.owner());
        assertThat(api.maxRequests()).isEqualTo(request.maxRequests());
        assertThat(api.description()).isEqualTo(request.description());
        assertThat(api.openApiUrl()).isEqualTo(request.openApiUrl());
        assertThat(api.authenticationType()).isEqualTo(request.authenticationType());
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            /jp, 201
            /jp, 500
            /jp2, 201
            jp, 400
            , 400
            """)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void saveEdgeCases(String proxyPath, int expectedStatus) {
        var request = Instancio.of(apiModel)
                .set(field(ApiPOST::proxyPath), proxyPath)
                .create();

        createApi(request, expectedStatus);
    }

    @Test
    @TestSecurity(user = "bob", authMechanism = "basic")
    void update() {
        var request = Instancio.of(apiModel).create();
        var apiId = createApi(request);

        var updateRequest = Map.of("proxyPath", "/bin", "description", "", "authenticationType", "");
        given().contentType(APPLICATION_JSON).body(updateRequest).put(apiId).then().statusCode(200);

        var api = getApiById(apiId);
        assertThat(api.proxyPath()).isEqualTo("/bin");
        assertThat(api.description()).isEqualTo("");
        assertThat(api.authenticationType()).isEqualTo(PASSTHROUGH);
    }

    @Test
    @TestSecurity(user = "bob", authMechanism = "basic")
    void credential() {
        // create subscription first
        var subKey = createSubscription("APIM Corporation");

        // create an api with a credential
        var request = Instancio.of(ApiPOST.class)
                .set(field(ApiPOST::proxyPath), "/test")
                .set(field(ApiPOST::authenticationType), BASIC)
                .set(field(ApiPOST::credential), new ApiCredential(subKey, "bob", "password", null, null, null, null, null, null, null))
                .withSettings(settings)
                .create();

        var apiId = createApi(request);
        var api = getApiById(apiId);
        assertThat(api.authenticationType()).isEqualTo(BASIC);

        // check subscription contains the credential
        var subscription = getSubscription(subKey);
        assertThat(subscription.credentials()).hasSize(1).element(0).extracting(cred -> cred.password()).isEqualTo("password");

        // update the credential
        updateCredential(apiId, Map.of("subscriptionKey", subKey, "password", "password2"));

        // validate the update
        subscription = getSubscription(subKey);
        assertThat(subscription.credentials()).hasSize(1);
        assertThat(subscription.credentials().get(0).password()).isEqualTo("password2");
    }

    private static String extractId(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String createApi(ApiPOST request) {
        return createApi(request, 201);
    }

    private static String createApi(ApiPOST request, int status) {
        var location =
                //@formatter:off
                given()
                    .accept(APPLICATION_JSON)
                    .contentType(APPLICATION_JSON)
                    .body(request)
                .when()
                    .post()
                .then()
                    .log().all()
                    .statusCode(status)
                    .extract().header(LOCATION);
        //@formatter:on

        var result = "";
        if (ValueRange.of(200, 204).isValidIntValue(status)) {
            result = extractId(location);
            assertThat(result).isNotBlank();
        }
        return result;
    }

    private static Api getApiById(String apiId) {
        return given().contentType(APPLICATION_JSON).get(apiId).then().statusCode(200).extract().as(Api.class);
    }

    String createSubscription(String subject) {
        //@formatter:off
        var location =
                given()
                    .accept(APPLICATION_JSON)
                    .contentType(APPLICATION_JSON)
                    .body(Map.of("subject", subject))
                .when()
                    .post(this.subscriptionsUrl)
                .then()
                        .statusCode(201)
                        .extract().header(LOCATION);
        //@formatter:on
        return extractId(location);
    }

    private SubscriptionAll getSubscription(String subKey) {
        return given().contentType(APPLICATION_JSON).get(this.subscriptionsUrl + "/{key}", subKey).then().statusCode(200).extract().as(SubscriptionAll.class);
    }

    private void updateCredential(String apiId, Map<String, Object> request) {
        given().contentType(APPLICATION_JSON).body(request).when().put("/{apiId}/credentials", apiId).then().statusCode(200);
    }
}