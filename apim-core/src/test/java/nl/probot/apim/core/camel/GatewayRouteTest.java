package nl.probot.apim.core.camel;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.ValidatableResponse;
import nl.probot.apim.core.entities.ApiKeyLocation;
import nl.probot.apim.core.entities.AuthenticationType;
import nl.probot.apim.core.rest.ApiController;
import nl.probot.apim.core.rest.SubscriptionController;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import org.apache.camel.http.common.HttpMethods;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.instancio.Instancio;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Map;
import java.util.Objects;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.RestHelper.addApi;
import static nl.probot.apim.core.RestHelper.addCredential;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.createSubscription;
import static nl.probot.apim.core.entities.ApiKeyLocation.HEADER;
import static nl.probot.apim.core.entities.AuthenticationType.API_KEY;
import static nl.probot.apim.core.entities.AuthenticationType.NONE;
import static org.apache.camel.http.common.HttpMethods.GET;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.instancio.Select.field;
import static org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE;

@QuarkusTest
class GatewayRouteTest {

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

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkSubscriptions(boolean subscriptionExists) {
        if (subscriptionExists) {
            var subKey = createSubscriptionWithApi("Sub-%b".formatted(subscriptionExists), "/bin", "https://httpbin.org");
            makeApiCall(subKey, "get", "/bin/get", 200);
        } else {
            makeApiCall("nonExistingKey", "get", "/bin/get", 404);
        }
    }

    @ParameterizedTest
    @EnumSource(value = HttpMethods.class, names = {"HEAD", "OPTIONS", "TRACE"}, mode = EXCLUDE)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkHttpMethods(HttpMethods method) {
        var methodLowercase = method.name().toLowerCase();
        var subKey = createSubscriptionWithApi("Sub-%s".formatted(method), "/bin/v-%s".formatted(methodLowercase), "https://httpbin.org");

        makeApiCall(subKey, method.name(), "/bin/v-%s/%s".formatted(methodLowercase, methodLowercase), 200)
                .header("Authorization", nullValue())
                .header("X-Forward-For", notNullValue())
                .header("subscription-key", nullValue())
                .body("headers.subscription-key", nullValue())
                .body("headers.Authorization", nullValue());
    }

    @NullSource
    @ParameterizedTest
    @EnumSource(AuthenticationType.class)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkAuthententicationTypes(AuthenticationType type) {
        var typeStr = Objects.requireNonNullElse(type, "null");
        var path = "/bin/v2-%s".formatted(typeStr).toLowerCase();
        var subKey = createSubscriptionWithApi(
                "sub-%s".formatted(typeStr),
                path,
                "https://httpbin.org",
                type,
                1000,
                true
        ).get("subKey");

        var response = makeApiCall(subKey, GET.name(), path + "/get", 200);
        if (type == null) {
            response.body("headers.Authorization", nullValue());
        } else {
            switch (type) {
                case CLIENT_CREDENTIALS -> response.body("headers.Authorization", equalTo("Bearer 123456"));
                case BASIC -> response.body("headers.Authorization", notNullValue());
                case NONE, PASSTHROUGH -> response.body("headers.Authorization", nullValue());
                case API_KEY -> response.header("ApiKey", is("token-12345"));
            }
        }
    }

    @ParameterizedTest
    @CsvSource(emptyValue = "", nullValues = "", textBlock = """
            HEADER, ApiKey, token-12345, 204
            HEADER, Authorization, ApiKey token-12345, 204
            HEADER, Authorization, token-12345, 204
            HEADER, , token-12345, 400
            HEADER, ApiKey, , 400
            HEADER, , , 400
            QUERY, ApiKey, token-12345, 204
            QUERY, ApiKey, , 400
            QUERY, , token-12345, 400
            QUERY, , , 400
            """)
    @TestSecurity(user = "bob", authMechanism = "basic")
    void checkApiKeyAuthententication(ApiKeyLocation location, String apiKeyHeader, String apiKey, int status) {
        var path = "/bin/v2-%s%s%s".formatted(location, apiKeyHeader, apiKey).replace(" ", "-").toLowerCase();
        var response = createSubscriptionWithApi(
                "sub-%s".formatted(path),
                path,
                "https://httpbin.org",
                API_KEY,
                1000,
                false
        );

        var subKey = response.get("subKey");
        var apiId = response.get("apiId");
        var apiCredential = createApiCredential(subKey, location, apiKeyHeader, apiKey);
        addCredential(apiId, apiCredential, status, this.apisUrl);

        if (status == 204) {
            var resp = makeApiCall(subKey, GET.name(), path + "/get", 200);
            if (location == HEADER) {
                if ("Authorization".equals(apiKeyHeader)) {
                    resp.body("headers.Authorization", is(apiKey));
                } else {
                    resp.header(apiKeyHeader, is(apiKey));
                }
            } else {
                resp.body("url", is("https://httpbin.org/get?ApiKey=token-12345"));
            }
        }
    }

    private String createSubscriptionWithApi(String subject, String path, String url) {
        return createSubscriptionWithApi(subject, path, url, NONE, 1000, false).get("subKey");
    }

    private Map<String, String> createSubscriptionWithApi(String subject, String path, String url, AuthenticationType authenticationType, int maxRequest, boolean withCredentials) {
        var subKey = createSubscription(subject, this.subscriptionsUrl);

        var request = Instancio.of(ApiPOST.class)
                .set(field(ApiPOST::proxyPath), path)
                .set(field(ApiPOST::proxyUrl), url)
                .set(field(ApiPOST::authenticationType), authenticationType)
                .set(field(ApiPOST::maxRequests), maxRequest)
                .withSettings(settings);

        if (withCredentials) {
            if (authenticationType == null) {
                request.set(field(ApiPOST::credential), new ApiCredential(subKey, "bob", "password", null, null, null, null, null, null, null));
            } else {
                var credential = switch (authenticationType) {
                    case NONE, PASSTHROUGH, BASIC -> new ApiCredential(subKey, "bob", "password", null, null, null, null, null, null, null);
                    case CLIENT_CREDENTIALS -> new ApiCredential(subKey, null, null, "clientId", "12345", serverUrl() + "/mock/auth", null, null, null, null);
                    case API_KEY -> new ApiCredential(subKey, null, null, null, null, null, null, "token-12345", "ApiKey", HEADER);
                };
                request.set(field(ApiPOST::credential), credential);
            }
        } else {
            request.ignore(field(ApiPOST::credential));
        }

        var apiId = createApi(request.lenient().create(), this.apisUrl);
        addApi(subKey, apiId, 200, this.subscriptionsUrl);
        return Map.of("subKey", subKey, "apiId", apiId);
    }

    private ApiCredential createApiCredential(String subKey, ApiKeyLocation location, String apiKeyHeader, String apiKey) {
        return new ApiCredential(subKey, null, null, null, null, null, null, apiKey, apiKeyHeader, location);
    }

    private ValidatableResponse makeApiCall(String subKey, String method, String path, int status) {
        return
                //@formatter:off
            given()
                    .contentType(APPLICATION_JSON)
                    .header("subscription-key", subKey)
//                    .log().all()
            .when()
                    .request(method, "%s%s%s".formatted(serverUrl(), this.apimContextRoot, path))
            .then()
                    .log().all()
                    .statusCode(status);
           //@formatter:on
    }

    private String serverUrl() {
        // remove last slash
        return this.serverUrl.substring(0, this.serverUrl.length() - 1);
    }
}