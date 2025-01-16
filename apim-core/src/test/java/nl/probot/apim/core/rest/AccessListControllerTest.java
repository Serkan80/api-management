package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.response.ValidatableResponse;
import nl.probot.apim.core.rest.dto.AccessList;
import nl.probot.apim.core.rest.dto.AccessListPOST;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.Objects.requireNonNullElse;
import static nl.probot.apim.core.InstancioHelper.settings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.instancio.Select.field;

@QuarkusTest
@TestHTTPEndpoint(AccessListController.class)
class AccessListControllerTest {


    @ParameterizedTest
    @CsvSource(textBlock = """
            1.1.1.1, true, , 201
            1.1.1.2, , true, 201
            1.1.1.1, , true, 400
            1.1.1.3, true, true, 400
            1.1.1.0/24, , true, 201
            1.1.1.0/24, , true, 400
            1.1.1, , true, 400
            hello, , true, 400
            e097:6df1:f3fc:1982:d2ae:5a46:1731:97fb, true, , 201
            e097:6df1:f3fc:1982:d2ae:5a46:1731:97fb, true, , 400
            e097:6df1:f3fc:1982:d2ae:5a46:1731:97fb, true, , 400
            fdb8:7e2c:53d6:906d::/64, true, , 201
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void saveAccessList(String ip, Boolean whitelisted, Boolean blacklisted, int expectedStatus) {
        var request = Instancio.of(AccessListPOST.class)
                .set(field(AccessListPOST::ip), ip)
                .set(field(AccessListPOST::whitelisted), whitelisted)
                .set(field(AccessListPOST::blacklisted), blacklisted)
                .withSettings(settings)
                .create();

        createAccessList(request, expectedStatus);

        if (expectedStatus == 201) {
            getAccessLists()
                    .log().body()
                    .body("$", hasItem(hasEntry("ip", ip)))
                    .body("$", hasItem(hasEntry("updatedBy", "bob")))
                    .body("$", hasItem(hasEntry("createdAt", notNullValue())))
                    .body("$", hasItem(hasEntry("updatedAt", notNullValue())));
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            1.1.1.10,,,200
            ,hello,,200
            ,,true,200
            ,,,true,200
            ,,true,false,200
            ,,true,true,400
            1.1.1.20,new ip,true,false,200
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void update(String newIp, String description, Boolean whitelisted, Boolean blacklisted, int expectedStatus) {
        var request = Instancio.of(AccessListPOST.class)
                .generate(field(AccessListPOST::ip), gen -> gen.net().ip4())
                .set(field(AccessListPOST::whitelisted), true)
                .ignore(field(AccessListPOST::blacklisted))
                .withSettings(settings)
                .create();

        createAccessList(request, 201);

        // Update the access list
        given()
                .contentType(JSON)
                .body(new AccessListPUT(request.ip(), newIp, description, blacklisted, whitelisted))
                .when()
                .put()
                .then()
                .statusCode(expectedStatus);

        if (expectedStatus == 200) {
            getAccessLists()
                    .log().body()
                    .body("$", hasItem(hasEntry("ip", requireNonNullElse(newIp, request.ip()))))
                    .body("$", hasItem(hasEntry("updatedBy", "bob")))
                    .body("$", hasItem(hasEntry("createdAt", notNullValue())))
                    .body("$", hasItem(hasEntry("updatedAt", notNullValue())));
        }

    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void updateAccessList_shouldReturn404ForNonExistingAccessList() {
        AccessList nonExistingAccessList = this.testAccessList;
        nonExistingAccessList.setId(999L);

        given()
                .contentType(JSON)
                .body(nonExistingAccessList)
                .when()
                .put("/{id}", nonExistingAccessList.getId())
                .then()
                .statusCode(404);
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void findAll_shouldReturnListOfAccessLists() {
        // Create multiple access lists
        given()
                .contentType(JSON)
                .body(this.testAccessList)
                .when()
                .post()
                .then()
                .statusCode(200);

        AccessList secondAccessList = Instancio.of(AccessList.class)
                .ignore(field(AccessList::getId))
                .create();

        given()
                .contentType(JSON)
                .body(secondAccessList)
                .when()
                .post()
                .then()
                .statusCode(200);

        // Retrieve and verify the list
        List<AccessList> response = given()
                .when()
                .get()
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList(".", AccessList.class);

        assertThat(response).isNotNull();
        assertThat(response).hasSizeGreaterThanOrEqualTo(2);
        assertThat(response).extracting(AccessList::getName)
                .contains(this.testAccessList.getName(), secondAccessList.getName());
    }

    private static ValidatableResponse createAccessList(AccessListPOST request, int expectedStatus) {
        return given()
                .contentType(JSON)
                .body(request)
                .when()
                .post()
                .then()
                .statusCode(expectedStatus);
    }

    private static ValidatableResponse getAccessLists() {
        return given()
                .contentType(JSON)
                .when().get()
                .then();
    }
}

