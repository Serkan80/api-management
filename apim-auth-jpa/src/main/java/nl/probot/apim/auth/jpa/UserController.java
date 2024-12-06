package nl.probot.apim.auth.jpa;

import io.quarkus.logging.Log;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import nl.probot.apim.auth.jpa.dto.ChangePassword;
import nl.probot.apim.auth.jpa.dto.User;
import nl.probot.apim.auth.jpa.dto.UserPOST;
import nl.probot.apim.auth.jpa.dto.UserPUT;
import nl.probot.apim.auth.jpa.entities.UserEntity;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.StaticStatement;
import nl.probot.apim.commons.jpa.PanacheDyanmicQueryHelper.WhereStatement;
import org.jboss.resteasy.reactive.RestResponse;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.hibernate.jpa.QueryHints.HINT_READONLY;

@ApplicationScoped
@RolesAllowed("${apim.roles.manager}")
public class UserController implements UserOpenApi {

    @Override
    @Transactional
    public RestResponse<Void> save(UserPOST user) {
        var entity = UserEntity.add(user.username(), user.password().toCharArray(), user.email(), user.joinRoles());

        return RestResponse.created(URI.create(entity.id));
    }

    @Override
    @Transactional
    public void changePassword(ChangePassword request) {
        var username = request.username();
        var user = UserEntity.findByUsername(username);

        if (Boolean.FALSE.equals(user.enabled)) {
            throw new WebApplicationException("User %s is blocked".formatted(username), 400);
        }

        if (!UserEntity.passwordMatches(request.oldPassword().toCharArray(), user.password, user.salt)) {
            throw new WebApplicationException("Username and/or newPassword incorrect: %s".formatted(username), 400);
        }

        UserEntity.setCredentials(user, request.newPassword().toCharArray());
        Log.infof("User(username=%s****) password updated", username.substring(0, 3));
    }

    @Override
    @Transactional
    public RestResponse<Void> update(UUID id, UserPUT user) {
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

    @Override
    @Transactional
    public void activate(UUID id, Boolean enable) {
        UserEntity.activate(id.toString(), enable);
        Log.infof("User(id=%s***, enabled=%b) updated", id.toString().substring(0, 3), enable);
    }

    @Override
    public List<User> findAll() {
        return UserEntity.findAll()
                .withHint(HINT_READONLY, true)
                .project(User.class)
                .list();
    }
}
