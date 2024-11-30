package nl.probot.apim.auth.jpa;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.validation.Validation;

import java.util.Base64;

import static nl.probot.apim.commons.crypto.CryptoUtil.randomNonce;

/**
 * This class is intended to be used to create the first user for the database.
 * After that, the user should be manually inserted into the database.
 */
public class UserCreationUtil {

    public static void main(String[] args) {
//        var console = new Scanner(System.in);
        var console = System.console();

        System.out.println("Provide username: ");
        var username = console.readLine();

        System.out.println("\nProvide password: ");
        var password = new String(console.readPassword());

        System.out.println("\nRepeat password: ");
        var passwordRepeat = new String(console.readPassword());

        while (!password.equals(passwordRepeat)) {
            System.err.println("\nPasswords are not the same, repeat password: ");
            passwordRepeat = new String(console.readPassword());
        }

        System.out.println("\nProvide email: ");
        var email = console.readLine();

        System.out.println("\nProvide role(s), comma separated for multiple roles: ");
        var roles = console.readLine();

        var user = createUser(username, password.toCharArray(), email, roles);
        var errors = Validation.buildDefaultValidatorFactory().getValidator().validate(user);

        if (!errors.isEmpty()) {
            System.err.println("\n\n\033[0;31mProvided parameters are not valid: ");
            errors.forEach(err -> System.err.printf("\t- %s: %s\n", err.getPropertyPath().toString(), err.getMessage()));
            System.exit(-1);
        }

        var query = """
                INSERT INTO user(id, username, password, salt, email, roles, enabled)   
                VALUES(gen_random_uuid(), '%s', '%s', '%s', '%s', '%s', true);
                """.formatted(username, user.password, user.salt, email, user.roles);

        System.out.println("\n\n\n\033[0;32mRun this query inside your db: ");
        System.out.println(query);
        System.exit(0);
    }

    private static UserEntity createUser(String username, char[] password, String email, String roles) {
        var credentials = createCredentials(password);
        var user = new UserEntity();
        user.username = username;
        user.email = email;
        user.roles = roles;
        user.enabled = true;
        user.password = credentials.password();
        user.salt = credentials.salt();
        return user;
    }

    static Credentials createCredentials(char[] password) {
        var nonce = randomNonce(16);
        var salt = Base64.getEncoder().encodeToString(nonce);
        var hashedPass = BcryptUtil.bcryptHash(String.valueOf(password), 31, nonce);
        return new Credentials(salt, hashedPass);
    }

    public record Credentials(String salt, String password) {
    }
}
