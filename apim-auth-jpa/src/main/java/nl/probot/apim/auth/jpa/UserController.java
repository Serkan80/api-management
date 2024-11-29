package nl.probot.apim.auth.jpa;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.auth.jpa.dto.ChangePassword;
import nl.probot.apim.auth.jpa.dto.User;
import nl.probot.apim.auth.jpa.dto.UserPOST;
import nl.probot.apim.auth.jpa.dto.UserPUT;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@Path("/users")
@RolesAllowed("${apim.roles.user.mgmt}")
public class UserController {

    @POST
    @Transactional
    public RestResponse<Void> save(@Valid UserPOST user) {
        var entity = UserEntity.add(user.username(), user.password().toCharArray(), user.email(), user.joinRoles());

        return RestResponse.created(URI.create(entity.id));
    }

    @PUT
    @Transactional
    @Path("/password")
    public void changePassword(@Valid ChangePassword request) {
        var username = request.username();
        var user = UserEntity.findByUsername(username);
        if (Boolean.FALSE.equals(user.enabled)) {
            throw new WebApplicationException("User %s is blocked".formatted(username), 400);
        }

        if (!UserEntity.passwordMatches(request.oldPassword().toCharArray(), user.password, user.salt)) {
            throw new WebApplicationException("Username and/or newPassword incorrect: %s".formatted(username), 400);
        }

        var credentials = UserCreationUtil.createCredentials(request.newPassword().toCharArray());
        user.salt = credentials.salt();
        user.password = credentials.password();
        Log.infof("User(username=%s****) password updated", username.substring(0, 3));
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public RestResponse<Void> update(@RestPath UUID id, @Valid UserPUT user) {
        var helper = new PanacheDyanmicQueryHelper();
        var query = helper.statements(
                new StaticStatement("roles", user.roles()),
                new StaticStatement("email", user.email())
        ).buildUpdateStatement(new WhereStatement("id = :id", id.toString()));

        var count = UserEntity.update(query, helper.values());
        if (count > 0) {
            Log.infof("User(id=%s***, email=%s, roles=%s) updated with %d records", id, user.email(), user.roles(), count);
            return RestResponse.ok();
        }
        return RestResponse.notFound();
    }

    @POST
    @Transactional
    @Path("/{id}/activate")
    public void activate(@RestPath UUID id, @RestQuery @NotNull Boolean enable) {
        UserEntity.activate(id.toString(), enable);
        Log.infof("User(id=%s***, enabled=%b) updated", id.toString().substring(0, 3), enable);
    }

    @GET
    public List<User> findAll() {
        return UserEntity.findAll()
                .withHint(HINT_READONLY, true)
                .project(User.class)
                .list();
    }
}
