package nl.probot.apim.auth.jpa;

import io.quarkus.logging.Log;
import io.quarkus.security.AuthenticationFailedException;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.request.UsernamePasswordAuthenticationRequest;
import io.quarkus.security.jpa.runtime.JpaIdentityProvider;
import io.quarkus.security.runtime.QuarkusPrincipal;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import nl.probot.apim.auth.jpa.entities.UserEntity;

import java.time.OffsetDateTime;

@Singleton
@Priority(1)
public class UserIdentityProvider extends JpaIdentityProvider {

    @Override
    public SecurityIdentity authenticate(EntityManager em, UsernamePasswordAuthenticationRequest request) {
        try {
            var user = em.createQuery("from UserEntity u where u.username = ?1", UserEntity.class)
                    .setParameter(1, request.getUsername())
                    .getSingleResult();

            if (Boolean.FALSE.equals(user.enabled)) {
                throw new AuthenticationFailedException("User %s is blocked".formatted(request.getUsername()));
            }

            if (!UserEntity.passwordMatches(request.getPassword().getPassword(), user.password, user.salt)) {
                throw new AuthenticationFailedException("Username and/or newPassword incorrect: %s".formatted(request.getUsername()));
            }

            logUser(em, user.username);
            return QuarkusSecurityIdentity.builder()
                    .setPrincipal(new QuarkusPrincipal(user.username))
                    .addRoles(user.splitRoles())
                    .addAttribute("email", user.email)
                    .build();
        } catch (Exception e) {
            Log.errorf("Unknown user login attempt: '%s'", request.getUsername());
            throw new AuthenticationFailedException("Unknown User");
        }
    }

    private static void logUser(EntityManager em, String username) {
        em.getTransaction().begin();
        UserEntity.update("lastLoggedIn = ?1 where username = ?2", OffsetDateTime.now(), username);
        em.getTransaction().commit();
        Log.infof("User %s******* logged in", username.substring(0, 3));
    }
}
