package nl.probot.apim.auth.jpa.entities;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.security.jpa.Password;
import io.quarkus.security.jpa.Roles;
import io.quarkus.security.jpa.UserDefinition;
import io.quarkus.security.jpa.Username;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.NotFoundException;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.TimeZoneStorage;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;

import static io.quarkus.logging.Log.infof;
import static nl.probot.apim.commons.crypto.CryptoUtil.randomNonce;
import static org.hibernate.annotations.TimeZoneStorageType.NATIVE;

@Entity
@UserDefinition
@Table(name = "user", schema = "public")
public class UserEntity extends PanacheEntityBase {

    @Id
    @GeneratedValue
    @UuidGenerator
    public String id;

    @NotBlank
    @Username
    @NaturalId
    @Size(min = 8, max = 50)
    @Column(unique = true)
    public String username;

    @NotBlank
    @Password
    @Size(min = 8, max = 500)
    public String password;

    @Email
    @NotBlank
    @Column(unique = true)
    public String email;

    @NotBlank
    public String salt;

    @Roles
    @NotBlank
    public String roles;

    public Boolean enabled = false;

    @TimeZoneStorage(NATIVE)
    @Column(name = "last_logged_in")
    public OffsetDateTime lastLoggedIn;

    public Set<String> splitRoles() {
        return Set.of(this.roles.split(","));
    }

    public static UserEntity findByUsername(String username) {
        return find("username = ?1", username)
                .<UserEntity>firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public static UserEntity add(String username, char[] password, String email, String roles) {
        return add(username, password, email, roles, true);
    }

    @Transactional
    public static UserEntity add(String username, char[] password, String email, String roles, boolean enabled) {
        var user = new UserEntity();
        user.username = username;
        user.email = email;
        user.roles = roles;
        user.enabled = enabled;
        setCredentials(user, password);
        user.persist();
        infof("User(name=%s***, roles=%s) created", username.substring(0, 3), roles);

        return user;
    }

    @Transactional
    public static void activate(String id, boolean activate) {
        update("enabled = ?2 where id = ?1", id, activate);
        infof("User(id=%s****, enabled=%b) updated", id.substring(0, 3), activate);
    }

    public static boolean passwordMatches(char[] password, String hashedPassword, String salt) {
        return hashedPassword.equals(hashPassword(password, salt));
    }

    public static String hashPassword(char[] password, String salt) {
        return BcryptUtil.bcryptHash(String.valueOf(password), 31, Base64.getDecoder().decode(salt));
    }

    public static void setCredentials(UserEntity user, char[] password) {
        var nonce = randomNonce(16);
        user.salt = Base64.getEncoder().encodeToString(nonce);
        user.password = BcryptUtil.bcryptHash(String.valueOf(password), 31, nonce);
    }
}
