package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import nl.probot.apim.core.rest.dto.Api;
import nl.probot.apim.core.rest.dto.ApiPOST;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.getApiById;
import static nl.probot.apim.core.RestHelper.updateApi;
import static nl.probot.apim.core.entities.AuthenticationType.PASSTHROUGH;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@QuarkusTest
@TestInstance(PER_CLASS)
@TestHTTPEndpoint(ApiController.class)
class ApiControllerTest {

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
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
            /jp/v2, 201, 1
            jp, 400, 0
            , 400, 0
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void saveEdgeCases(String proxyPath, int expectedStatus, int totalApis) {
        var request = Instancio.of(apiModel)
                .set(field(ApiPOST::proxyPath), proxyPath)
                .create();

        createApi(request, expectedStatus, null);

        if (proxyPath != null && !proxyPath.isBlank()) {
            var apis = given().contentType(APPLICATION_JSON).get().thenReturn().as(Api[].class);
            assertThat(apis).filteredOn(api -> api.proxyPath().equals(proxyPath)).hasSize(totalApis);
        }
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void update() {
        var request = Instancio.of(apiModel).create();
        var apiId = createApi(request, null);

        updateApi(apiId, 200, null, Map.of("proxyPath", "/bin", "description", "", "authenticationType", ""));

        var api = getApiById(apiId);
        assertThat(api.proxyPath()).isEqualTo("/bin");
        assertThat(api.description()).isEqualTo("");
        assertThat(api.authenticationType()).isEqualTo(PASSTHROUGH);

        // update non existing api
        updateApi(1000L, 204, null, Map.of("proxyPath", "/bin/v2"));
    }
}