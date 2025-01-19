package nl.probot.apim.core.rest;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import nl.probot.apim.core.rest.dto.AccessList;
import nl.probot.apim.core.rest.dto.AccessListPOST;
import nl.probot.apim.core.rest.dto.AccessListPUT;
import org.instancio.Instancio;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static java.util.Objects.requireNonNullElse;
import static nl.probot.apim.core.InstancioHelper.settings;
import static nl.probot.apim.core.RestHelper.createAccessList;
import static nl.probot.apim.core.RestHelper.getAccessList;
import static nl.probot.apim.core.RestHelper.getAccessLists;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.instancio.Select.field;

@QuarkusTest
@TestHTTPEndpoint(AccessListController.class)
class AccessListControllerTest {

    @ParameterizedTest
    @CsvSource(textBlock = """
            1.1.1.1, true, , 201
            1.1.1.2, , true, 201
            1.1.1.1, , true, 500
            1.1.1.3, true, true, 400
            1.1.1.3, true, false, 201
            1.1.1.0/24, , true, 201
            1.1.1.0/24, , true, 500
            1.1.1, , true, 400
            hello, , true, 400
            e097:6df1:f3fc:1982:d2ae:5a46:1731:97fb, true, , 201
            e097:6df1:f3fc:1982:d2ae:5a46:1731:97fb, true, , 500
            e097:6df1:f3fc:1982:d2ae:5a46:1731:/64, true, , 400
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

        createAccessList(request, expectedStatus, null);

        if (expectedStatus == 201) {
            getAccessList(ip)
                    .statusCode(200)
                    .body("ip", is(ip))
                    .body("updatedBy", is("bob"))
                    .body("blacklisted", is(blacklisted))
                    .body("whitelisted", is(whitelisted))
                    .body("createdAt", startsWith(LocalDate.now().toString()))
                    .body("updatedAt", startsWith(LocalDate.now().toString()))
                    .body("description", is(request.description()));
        }

        if ("fdb8:7e2c:53d6:906d::/64".equals(ip)) {
            assertThat(getAccessLists()).hasSizeGreaterThanOrEqualTo(6).isSortedAccordingTo(Comparator.comparing(AccessList::ip));
        }
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            1.1.1.10,,,,200
            ,hello,,,200
            ,,true,,200
            ,,,true,200
            ,,true,false,200
            ,,true,true,400
            ,,false,true,200
            1.1.1.20,new ip,true,false,200
            """)
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void update(String newIp, String description, Boolean whitelisted, Boolean blacklisted, int expectedStatus) {
        var request = createAccessList();

        // Update the access list
        given()
                .contentType(JSON)
                .body(new AccessListPUT(request.ip(), newIp, description, blacklisted, whitelisted))
                .when()
                .put()
                .then()
                .statusCode(expectedStatus);

        var accessList = getAccessList(requireNonNullElse(newIp, request.ip()))
                .statusCode(200)
                .body("ip", is(requireNonNullElse(newIp, request.ip())))
                .body("updatedBy", is("bob"))
                .body("createdAt", startsWith(LocalDate.now().toString()))
                .body("updatedAt", startsWith(LocalDate.now().toString()))
                .body("description", is(requireNonNullElse(description, request.description())))
                .extract().as(AccessList.class);

        assertThat(accessList.blacklisted()).isNotEqualTo(accessList.whitelisted());
        if (accessList.blacklisted() != null) {
            assertThat(accessList.blacklisted()).isEqualTo(!accessList.whitelisted());
        }
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void updateNonExistingIP() {
        given()
                .contentType(JSON)
                .body(Map.of("ip", "1.2.3.4", "description", "non existing ip", "blacklisted", true))
                .when()
                .put()
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "bob", roles = "manager", authMechanism = "basic")
    void delete() {
        var request = createAccessList();
        given().contentType(JSON).delete("/{ip}", request.ip()).then().statusCode(200);
        getAccessList(request.ip()).statusCode(404);
    }
}

