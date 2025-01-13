package nl.probot.apim.core.rest;

import io.quarkus.narayana.jta.QuarkusTransaction;
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
import nl.probot.apim.core.rest.dto.SubscriptionPOST;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URL;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static nl.probot.apim.core.InstancioHelper.apiModel;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.RestHelper.addApi;
import static nl.probot.apim.core.RestHelper.addCredential;
import static nl.probot.apim.core.RestHelper.createApi;
import static nl.probot.apim.core.RestHelper.createSubscription;
import static nl.probot.apim.core.RestHelper.getApiById;
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
            addApi("nonExistingSubKey", apiId, status, null);
            var subs = getSubscriptions();
            assertThat(subs).hasSize(2);
        }

        addApi(subKey, apiId, status, null).ifPresent(subscription -> assertThat(subscription.apis()).hasSize(1));
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void cannotAddApiToDisabledSubscription() {
        var subKey = createSubscription("Organization v2 - Disabled", null);

        // disable sub
        given().contentType(APPLICATION_JSON).body(Map.of("enabled", false)).put("/{key}", subKey).then().statusCode(200);

        var apiId = createApi(Instancio.of(apiModel).create(), this.apisUrl);
        addApi(subKey, apiId, 404, null);
    }

    @ParameterizedTest
    @CsvSource(delimiter = ';', textBlock = """
            ;400
            bob1,bob2;201
            bob3;201
            bob4,bob1;400
            bob10,bob11,bob12,bob13,bob15,bob16,bob17,bob18,bob19,bob20,bob21,bob22,bob23,bob24,bob25,bob26,bob27,bob28,bob29,bob30;201
            bob30,bob31,bob32,bob33,bob35,bob36,bob37,bob38,bob39,bob40,bob41,bob42,bob43,bob44,bob45,bob46,bob47,bob48,bob49,bob50,bob51;400
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void subscriptionWithAccounts(String accounts, int expectedStatus) {
        var accountsArray = Set.of();
        if (accounts != null) {
            accountsArray = Set.of(accounts.split(","));
        }

        var subscriptionPOST = Instancio.of(SubscriptionPOST.class)
                .ignore(field(SubscriptionPOST::endDate))
                .set(field(SubscriptionPOST::accounts), accountsArray)
                .withSettings(settings)
                .create();

        createSubscription(subscriptionPOST, expectedStatus, null);
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

    @ParameterizedTest
    @CsvSource(textBlock = """
            -1,200
            0,200
            1,204
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void cleanupOldSubscriptions(int days, int expectedStatus) {
        var apiId = createApi(Instancio.of(apiModel).create(), this.apisUrl);

        QuarkusTransaction.begin();
        var subEntity = Instancio.of(SubscriptionEntity.class)
                .set(field("enabled"), true)
                .set(field("endDate"), LocalDate.now().plusDays(days))
                .ignore(field("apis"))
                .ignore(field("apiCredentials"))
                .withSettings(settings)
                .create();

        subEntity.id = null;
        subEntity.addApi(ApiEntity.findById(apiId));
        subEntity.persist();
        QuarkusTransaction.commit();

        addCredential(new ApiCredential(subEntity.subscriptionKey, apiId, "bob2", "password2", null, null, null, null, null, null, null), 200, null);
        given().contentType(APPLICATION_JSON).delete("/cleanup").then().statusCode(expectedStatus).log().all();

        getSubscription(subEntity.subscriptionKey, expectedStatus == 200 ? 404 : 200, null);
        assertThat(getApiById(apiId, this.apisUrl)).isNotNull();
        assertThat(ApiCredentialEntity.<ApiCredentialEntity>listAll()).extracting(cred -> cred.id.subscription.id).isNotEqualTo(subEntity.id);
    }

    private Subscription[] getSubscriptions() {
        return given().contentType(APPLICATION_JSON).when().get().thenReturn().as(Subscription[].class);
    }
}