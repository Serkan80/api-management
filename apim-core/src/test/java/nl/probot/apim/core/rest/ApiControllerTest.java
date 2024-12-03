package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.URL;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.createSubscription;
import static nl.probot.apim.core.RestHelper.getApiById;
import static nl.probot.apim.core.RestHelper.getSubscription;
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
        var apiId = createApi(request, null);
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
            /jp, 201, 1
            /jp, 500, 1
            /jp/v2, 201, 2
            jp, 400, 2
            , 400, 2
            """)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void saveEdgeCases(String proxyPath, int expectedStatus, int totalApis) {
        var request = Instancio.of(apiModel)
                .set(field(ApiPOST::proxyPath), proxyPath)
                .create();

        createApi(request, expectedStatus, null);

        var apis = given().contentType(APPLICATION_JSON).get().thenReturn().as(Api[].class);
        assertThat(apis.length).isEqualTo(totalApis);
    }

    @Test
    @TestSecurity(user = "bob", authMechanism = "basic")
    void update() {
        var request = Instancio.of(apiModel).create();
        var apiId = createApi(request, null);

        updateApi(apiId, 200, Map.of("proxyPath", "/bin", "description", "", "authenticationType", ""));

        var api = getApiById(apiId);
        assertThat(api.proxyPath()).isEqualTo("/bin");
        assertThat(api.description()).isEqualTo("");
        assertThat(api.authenticationType()).isEqualTo(PASSTHROUGH);

        // update non existing api
        updateApi("1000", 204, Map.of("proxyPath", "/bin/v2"));
    }

    @Test
    @TestSecurity(user = "bob", authMechanism = "basic")
    void addCredential() {
        // create subscription first
        var subKey = createSubscription("APIM Corporation", this.subscriptionsUrl);

        // create an api with a credential
        var request = Instancio.of(ApiPOST.class)
                .set(field(ApiPOST::proxyPath), "/test")
                .set(field(ApiPOST::authenticationType), BASIC)
                .set(field(ApiPOST::credential), new ApiCredential(subKey, "bob", "password", null, null, null, null, null, null, null))
                .withSettings(settings)
                .create();

        var apiId = createApi(request, null);
        var api = getApiById(apiId);
        assertThat(api.authenticationType()).isEqualTo(BASIC);

        // check subscription contains the credential
        var subscription = getSubscription(subKey, this.subscriptionsUrl);
        assertThat(subscription.credentials()).hasSize(1).element(0).extracting(ApiCredential::password).isEqualTo("password");

        // update the credential
        updateCredential(apiId, Map.of("subscriptionKey", subKey, "password", "password2"));

        // validate the update
        subscription = getSubscription(subKey, this.subscriptionsUrl);
        assertThat(subscription.credentials()).hasSize(1);
        assertThat(subscription.credentials().get(0).password()).isEqualTo("password2");
    }

    private static void updateApi(String apiId, int status, Map<String, String> updateRequest) {
        given().contentType(APPLICATION_JSON).body(updateRequest).put(apiId).then().statusCode(status);
    }

    private void updateCredential(String apiId, Map<String, Object> request) {
        given().contentType(APPLICATION_JSON).body(request).when().put("/{apiId}/credentials", apiId).then().statusCode(200);
    }
}