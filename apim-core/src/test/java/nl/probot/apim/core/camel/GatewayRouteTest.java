package nl.probot.apim.core.camel;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.builder.MultiPartSpecBuilder;
import io.restassured.response.ValidatableResponse;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.ApiKeyLocation;
import nl.probot.apim.core.entities.AuthenticationType;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.rest.ApiController;
import nl.probot.apim.core.rest.SubscriptionController;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import org.apache.camel.http.common.HttpMethods;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.RestHelper.addApi;
import static nl.probot.apim.core.RestHelper.addCredential;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.createSubscription;
import static nl.probot.apim.core.RestHelper.updateApi;
import static nl.probot.apim.core.entities.ApiKeyLocation.HEADER;
import static nl.probot.apim.core.entities.AuthenticationType.API_KEY;
import static org.apache.camel.http.common.HttpMethods.GET;
import static org.apache.camel.http.common.HttpMethods.HEAD;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;
import static org.junit.platform.commons.util.StringUtils.isBlank;

@QuarkusTest
@TestInstance(PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
class GatewayRouteTest {

    static final String PROXY_PATH = "/mockserver";

    @ConfigProperty(name = "apim.context-root")
    String apimContextRoot;

    @TestHTTPResource
    String serverUrl;

    @TestHTTPResource
    @TestHTTPEndpoint(ApiController.class)
    URL apisUrl;

    @TestHTTPResource
    @TestHTTPEndpoint(SubscriptionController.class)
    URL subscriptionsUrl;

    Long mainSubId;
    String mainSubKey;
    String apiId;

    @BeforeEach
    @TestSecurity(user = "bob", authMechanism = "basic")
    void init() {
        if (isBlank(this.mainSubKey)) {
            var response = createSubscriptionWithApi("Main Test Subscription", PROXY_PATH);
            this.mainSubKey = response.subKey;
            this.mainSubId = response.subId;
            this.apiId = response.apiId;
        }
    }

    @Test
    @Order(0)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void rateLimit() {
        updateApi(this.apiId, 200, this.apisUrl, Map.of("maxRequests", "1"));
        triggerRateLimit(PROXY_PATH, 1)
                .body("exception", equalTo("org.apache.camel.processor.ThrottlerRejectedExecutionException"))
                .body("message", equalTo("Exceeded the max throttle rate of 1 within 60000ms"));

        // another subscription should be able to call the same api without being blocked
        var response = createSubscriptionWithApi("Another sub", "/some/path");
        addApi(response.subKey, this.apiId, 200, this.subscriptionsUrl);
        makeApiCall(response.subKey, GET.name(), PROXY_PATH);

        // undo rate limit
        updateApi(this.apiId, 200, this.apisUrl, Map.of("maxRequests", "1000"));
    }

    @Order(1)
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkSubscriptionKey(boolean subscriptionExists) {
        if (subscriptionExists) {
            makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH);
        } else {
            makeApiCall("nonExistingKey", GET.name(), PROXY_PATH, 404);
        }
    }

    @Order(2)
    @ParameterizedTest
    @EnumSource(value = HttpMethods.class)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkHttpMethods(HttpMethods method) {
        var response = makeApiCall(this.mainSubKey, method.name(), PROXY_PATH);

        response
                .header("Authorization", nullValue())
                .header("X-Forward-For", notNullValue())
                .header("subscription-key", nullValue());

        if (method != HEAD) {
            response.body("method", equalTo(method.name()))
                    .body("headers.subscription-key", nullValue())
                    .body("headers.Authorization", nullValue());
        }
    }

    @Test
    @Order(3)
    @TestSecurity(user = "bob", authMechanism = "basic")
    public void disabledApiShouldNotBeAccessible() {
        updateApi(this.apiId, 200, this.apisUrl, Map.of("enabled", "false"));
        makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH, 404);
        updateApi(this.apiId, 200, this.apisUrl, Map.of("enabled", "true"));
    }

    @Test
    @Order(4)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void notLinkedApiShouldNotBeAccessible() {
        createApi(Instancio.of(apiModel).set(field(ApiPOST::proxyPath), "/forbidden").create(), this.apisUrl);
        makeApiCall(this.mainSubKey, GET.name(), "/forbidden", 404)
                .body("exception", equalTo("jakarta.ws.rs.NotFoundException"))
                .body("message", equalTo("Api(proxyPath=/forbidden) not found or was not enabled"));
    }

    @Test
    @Order(500)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void multipart() {
        // add multipart api to this subscription
        var request = Instancio.of(apiModel)
                .set(field(ApiPOST::proxyPath), "/multipart")
                .set(field(ApiPOST::proxyUrl), serverUrl() + "/multipart")
                .set(field(ApiPOST::maxRequests), 1000)
                .ignore(field(ApiPOST::authenticationType))
                .create();

        var multiPartApiId = createApi(request, this.apisUrl);
        addApi(this.mainSubKey, multiPartApiId, 200, this.subscriptionsUrl);

        var picture = getClass().getClassLoader().getResourceAsStream("picture.jpg");
        makeMultipartCall(this.mainSubKey, "picture", picture)
                .statusCode(200)
                .rootPath("[0]")
                .body("name", equalTo("picture"))
                .body("size", lessThan(1024 * 1024));
    }

    @Order(501)
    @ParameterizedTest
    @CsvSource(textBlock = """
            1, 413
            2, 413
            """)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void bigUploads(int sizeMB, int status) {
        var data = new ByteArrayInputStream(createDummyContent(sizeMB));
        makeMultipartCall(this.mainSubKey, "dummy", data).statusCode(status);
    }

    @Order(799)
    @NullSource
    @ParameterizedTest
    @EnumSource(AuthenticationType.class)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkAuthententicationTypes(AuthenticationType type) {
        updateApi(this.apiId, 200, this.apisUrl, Map.of("authenticationType", type != null ? type.name() : ""));

        var response = makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH);
        if (type == null) {
            response.header("Authorization", nullValue());
        } else {
            switch (type) {
                case CLIENT_CREDENTIALS -> response.body("headers.Authorization", equalTo("Bearer 123456"));
                case BASIC -> response.body("headers.Authorization", notNullValue());
                case PASSTHROUGH -> response.body("headers.Authorization", nullValue());
                case API_KEY -> response.header("ApiKey", is("token-12345"));
            }
        }
    }

    @Order(800)
    @ParameterizedTest
    @CsvSource(emptyValue = "", nullValues = "", textBlock = """
            HEADER, ApiKey, token-12345, 200
            HEADER, Authorization, ApiKey token-12345, 200
            HEADER, Authorization, token-12345, 200
            HEADER, , token-12345, 500
            HEADER, ApiKey, , 500
            HEADER, , , 500
            QUERY, ApiKey, token-12345, 200
            QUERY, ApiKey, , 500
            QUERY, , token-12345, 500
            QUERY, , , 500
            """)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkApiKeyAuthententication(ApiKeyLocation location, String apiKeyHeader, String apiKey, int status) {
        updateApi(this.apiId, 200, this.apisUrl, Map.of("authenticationType", API_KEY.name()));
        QuarkusTransaction.begin();
        ApiCredentialEntity.update("apiKeyHeader = ?1, apiKey = ?2, apiKeyLocation = ?3 where id.subscription.id = ?4",
                                   apiKeyHeader, apiKey, location, this.mainSubId);
        QuarkusTransaction.commit();

        if (status == 200) {
            var resp = makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH);
            if (location == HEADER) {
                if (AUTHORIZATION.equals(apiKeyHeader)) {
                    resp.body("headers.Authorization", equalTo(apiKey));
                } else {
                    resp.header(apiKeyHeader, equalTo(apiKey));
                }
            } else {
                resp.body("url", equalTo("/mock/methods?ApiKey=token-12345"));
            }
        }
    }

    @Test
    @Order(900)
    @TestSecurity(user = "bob", authMechanism = "basic")
    public void disabledSubscriptionNotAccessible() {
        QuarkusTransaction.begin();
        SubscriptionEntity.update("enabled = false where id = ?1", this.mainSubId);
        QuarkusTransaction.commit();
        makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH, 404);

        QuarkusTransaction.begin();
        SubscriptionEntity.update("enabled = true where id = ?1", this.mainSubId);
        QuarkusTransaction.commit();

        updateApi(this.apiId, 200, this.apisUrl, Map.of("authenticationType", ""));
        makeApiCall(this.mainSubKey, GET.name(), PROXY_PATH);
    }

    private byte[] createDummyContent(int size) {
        return new byte[1024 * 1024 * size];
    }

    private ValidatableResponse triggerRateLimit(String path, int maxRequests) {
        for (int i = 0; i < maxRequests; i++) {
            makeApiCall(this.mainSubKey, GET.name(), path);
        }

        // +1 call should trigger rate limit max
        return makeApiCall(this.mainSubKey, GET.name(), path, 500);
    }

    private ValidatableResponse makeApiCall(String subKey, String method, String path) {
        return makeApiCall(subKey, method, path, 200);
    }

    private ValidatableResponse makeApiCall(String subKey, String method, String path, int status) {
        return
                //@formatter:off
            given()
                    .contentType(APPLICATION_JSON)
                    .header("subscription-key", subKey)
                    .log().all()
            .when()
                    .request(method, "%s%s%s".formatted(serverUrl(), this.apimContextRoot, path))
            .then()
                    .log().all()
                    .statusCode(status);
           //@formatter:on
    }

    private ValidatableResponse makeMultipartCall(String subKey, String filename, InputStream data) {
        return
                //@formatter:off
                given()
                        .header("subscription-key", subKey)
                        .multiPart(new MultiPartSpecBuilder(data).fileName(filename).controlName(filename).build())
                        .log().all()
                .when()
                        .post("%s%s%s".formatted(serverUrl(), this.apimContextRoot, "/multipart"))
                .then()
                        .log().all();
        //@formatter:on
    }

    private SubscriptionResponse createSubscriptionWithApi(String subject, String proxyPath) {
        var subKey = createSubscription(subject, this.subscriptionsUrl);
        var subId = SubscriptionEntity.getByNaturalId(subKey).id;
        var request = Instancio.of(apiModel)
                .set(field(ApiPOST::proxyPath), proxyPath)
                .set(field(ApiPOST::proxyUrl), "%s/mock/methods".formatted(serverUrl()))
                .ignore(field(ApiPOST::authenticationType))
                .set(field(ApiPOST::maxRequests), 1000)
                .create();

        var apiId = createApi(request, this.apisUrl);
        addApi(subKey, apiId, 200, this.subscriptionsUrl);
        addCredential(
                apiId,
                new ApiCredential(subKey, "bob", "password", "clientId", "12345", serverUrl() + "/mock/auth", null, "token-12345", "ApiKey", HEADER),
                200,
                this.apisUrl);

        return new SubscriptionResponse(subKey, subId, apiId);
    }

    private String serverUrl() {
        // remove last slash
        return this.serverUrl.substring(0, this.serverUrl.length() - 1);
    }

    private record SubscriptionResponse(String subKey, Long subId, String apiId) {}
}