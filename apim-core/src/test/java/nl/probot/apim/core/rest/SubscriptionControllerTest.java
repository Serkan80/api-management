package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.transaction.Transactional;
import nl.probot.apim.core.entities.ApiCredentialEntity;
import nl.probot.apim.core.entities.ApiEntity;
import nl.probot.apim.core.entities.SubscriptionEntity;
import nl.probot.apim.core.rest.dto.ApiCredential;
import nl.probot.apim.core.rest.dto.ApiPOST;
import nl.probot.apim.core.rest.dto.Subscription;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.RestHelper.addApi;
import static nl.probot.apim.core.RestHelper.addCredential;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.createSubscription;
import static nl.probot.apim.core.RestHelper.getSubscription;
import static nl.probot.apim.core.RestHelper.updateCredential;
import static nl.probot.apim.core.entities.AuthenticationType.BASIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@QuarkusTest
@TestInstance(PER_CLASS)
@TestHTTPEndpoint(SubscriptionController.class)
class SubscriptionControllerTest {

    @TestHTTPResource
    @TestHTTPEndpoint(ApiController.class)
    URL apisUrl;

    @BeforeAll
    @Transactional
    public void cleanup() {
        ApiCredentialEntity.deleteAll();
        ApiEntity.deleteAll();
        SubscriptionEntity.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void addApis(boolean addExistingApi) {
        var subKey = createSubscription("Organization v1 - %b".formatted(addExistingApi), null);
        var status = addExistingApi ? 200 : 204;
        var apiId = 1000L;

        if (addExistingApi) {
            apiId = createApi(Instancio.of(apiModel).create(), this.apisUrl);
            var subs = getSubscriptions();
            assertThat(subs).hasSize(1);
        } else {
            addApi("nonExistingSubKey", apiId, 404, null);
            var subs = getSubscriptions();
            assertThat(subs).hasSize(2);
        }

        addApi(subKey, apiId, status, null).ifPresent(subscription -> assertThat(subscription.apis()).hasSize(1));
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void credential() {
        var subKey = createSubscription("APIM Corporation", null);

        var request = Instancio.of(ApiPOST.class)
                .set(field(ApiPOST::proxyPath), "/test")
                .set(field(ApiPOST::authenticationType), BASIC)
                .withSettings(settings)
                .create();

        var apiId = createApi(request, this.apisUrl);
        addCredential(new ApiCredential(subKey, apiId, "bob", "password", null, null, null, null, null, null, null), 200, null);

        // check subscription contains the credential
        var subscription = getSubscription(subKey, null);
        assertThat(subscription.credentials()).hasSize(1).element(0).extracting(ApiCredential::password).isEqualTo("password");

        // update the credential
        updateCredential(Map.of("apiId", apiId, "subscriptionKey", subKey, "password", "password2"), null);

        // validate the update
        subscription = getSubscription(subKey, null);
        assertThat(subscription.credentials()).hasSize(1);
        assertThat(subscription.credentials().get(0).password()).isEqualTo("password2");
    }

    private Subscription[] getSubscriptions() {
        return given().contentType(APPLICATION_JSON).when().get().thenReturn().as(Subscription[].class);
    }
}