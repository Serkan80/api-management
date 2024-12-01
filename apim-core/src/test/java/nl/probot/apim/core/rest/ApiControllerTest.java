package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPOST;
import org.instancio.Instancio;
import org.instancio.Model;
import org.instancio.settings.Settings;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static jakarta.ws.rs.core.HttpHeaders.LOCATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.instancio.settings.Keys.BEAN_VALIDATION_ENABLED;

@QuarkusTest
@TestHTTPEndpoint(ApiController.class)
class ApiControllerTest {

    Settings settings = Settings.create().set(BEAN_VALIDATION_ENABLED, true);
    Model<ApiPOST> apiModel = Instancio.of(ApiPOST.class)
            .ignore(field(ApiPOST::credential))
            .withSettings(this.settings)
            .toModel();

    @Test
    void save() {
        var request = Instancio.of(this.apiModel).create();
        var url = given().body(request).when().post().then().extract().header(LOCATION);
        assertThat(url).isNotBlank().endsWith("/apis");

        var apis = when().get().thenReturn().as(Api[].class);
        assertThat(apis).hasSize(1);
        assertThat(apis[0].id()).isNotNull();
        assertThat(apis[0].enabled()).isEqualTo(true);
        assertThat(apis[0].proxyPath()).isEqualTo(request.proxyPath());
        assertThat(apis[0].proxyUrl()).isEqualTo(request.proxyUrl());
        assertThat(apis[0].owner()).isEqualTo(request.owner());
        assertThat(apis[0].maxRequests()).isEqualTo(request.maxRequests());
        assertThat(apis[0].description()).isEqualTo(request.description());
        assertThat(apis[0].openApiUrl()).isEqualTo(request.openApiUrl());
        assertThat(apis[0].authenticationType()).isEqualTo(request.authenticationType());
    }

    @Test
    void update() {
    }

    @Test
    void addCredential() {
    }

    @Test
    void updateCredential() {
    }

    private static String extractLocation(String location) {
        return location.substring(location.lastIndexOf('/') + 1);
    }
}