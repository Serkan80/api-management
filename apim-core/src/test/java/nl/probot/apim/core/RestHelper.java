package nl.probot.apim.core;

import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.SubscriptionAll;
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import org.instancio.Instancio;

import java.net.URL;
import java.time.temporal.ValueRange;
import java.util.Map;
import java.util.Optional;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.settings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;

public final class RestHelper {

    public static String createSubscription(String name, URL url) {
        var subscriptionPOST = Instancio.of(SubscriptionPOST.class)
                .set(field(SubscriptionPOST::name), name)
                .ignore(field(SubscriptionPOST::endDate))
                .withSettings(settings)
                .create();

        return createSubscription(subscriptionPOST, 201, url);
    }

    public static String createSubscription(SubscriptionPOST sub, int status, URL url) {
        var builder = given()
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .body(sub)
                .when()
                .post(getUrl(url))
                .then()
                .statusCode(status);

        if (ValueRange.of(200, 204).isValidIntValue(status)) {
            return extractId(builder.extract().header(LOCATION));
        } else {
            return null;
        }
    }

    public static SubscriptionAll getSubscription(String subKey, URL url) {
        return getSubscription(subKey, 200, url);
    }

    public static SubscriptionAll getSubscription(String subKey, int status, URL url) {
        var response = given().contentType(APPLICATION_JSON).get(getUrl(url) + "/{key}", subKey).then().statusCode(status);

        if (ValueRange.of(200, 200).isValidIntValue(status)) {
            return response.extract().as(SubscriptionAll.class);
        } else {
            return null;
        }
    }

    public static long createApi(ApiPOST request, URL url) {
        return createApi(request, 201, url);
    }

    public static long createApi(ApiPOST request, int status, URL url) {
        var location =
                //@formatter:off
                given()
                        .accept(APPLICATION_JSON)
                        .contentType(APPLICATION_JSON)
                        .body(request)
                .when()
                        .post(getUrl(url))
                .then()
                        .statusCode(status)
                        .log().body()
                        .extract().header(LOCATION);
        //@formatter:on

        var result = -1L;
        if (ValueRange.of(200, 204).isValidIntValue(status)) {
            var id = extractId(location);
            assertThat(id).isNotNull();

            result = Long.valueOf(id);
        }
        return result;
    }

    public static Api getApiById(Long apiId) {
        return getApiById(apiId, null);
    }

    public static Api getApiById(Long apiId, URL url) {
        return given().contentType(APPLICATION_JSON).get(getUrl(url) + "/{apiId}", apiId).then().statusCode(200).extract().as(Api.class);
    }

    public static Optional<SubscriptionAll> addApi(String subKey, Long apiId, int status, URL url) {
        //@formatter:off
        var builder =
                given()
                        .contentType(APPLICATION_JSON)
                        .body("[%d]".formatted(apiId))
                .when()
                        .post(getUrl(url) + "/{subKey}/apis", subKey)
                .then()
                        .statusCode(status);
        //@formatter:on

        if (status == 200) {
            return Optional.of(builder.extract().as(SubscriptionAll.class));
        }

        return Optional.empty();
    }

    public static void updateApi(Long apiId, int status, URL url, Map<String, String> updateRequest) {
        given().contentType(APPLICATION_JSON).body(updateRequest).put(getUrl(url) + "/{apiId}", apiId).then().statusCode(status);
    }

    public static void addCredential(ApiCredential request, int status, URL url) {
        given().contentType(APPLICATION_JSON).body(request).when().post(getUrl(url) + "/credentials").then().statusCode(status);
    }

    public static void updateCredential(Map<String, Object> request, URL url) {
        given().contentType(APPLICATION_JSON).body(request).when().put(getUrl(url) + "/credentials").then().statusCode(200);
    }

    public static String extractId(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }

    private static String getUrl(URL url) {
        return Optional.ofNullable(url).map(URL::toString).orElse("");
    }
}
