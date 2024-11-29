package nl.probot.apim.auth.jpa;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.validation.Validation;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Scanner;

/**
 * This class is intended to be used to create the first user for the database.
 * After that, the user should be manually inserted into the database.
 */
public class UserCreationUtil {

    public static void main(String[] args) {
        var console = new Scanner(System.in);

        System.out.println("Provide username: ");
        var username = console.nextLine();

        System.out.println("Provide newPassword: ");
        var password = console.nextLine();

        System.out.println("Repeat newPassword: ");
        var passwordRepeat = console.nextLine();

        if (!password.equals(passwordRepeat)) {
            System.err.println("Passwords are not the same");
            System.exit(-1);
        }

        System.out.println("Provide email: ");
        var email = console.nextLine();

        System.out.println("Provide role(s), comma separated for multiple roles: ");
        var roles = console.nextLine();

        var user = createUser(username, password.toCharArray(), email, roles);
        var errors = Validation.buildDefaultValidatorFactory().getValidator().validate(user);

        if (!errors.isEmpty()) {
            System.err.println("Provided parameters are not valid: ");
            errors.forEach(err -> System.err.println("\t\t" + err.getMessage()));
            System.exit(-1);
        }

        var query = """
                INSERT INTO user(id, username, newPassword, salt, email, roles, enabled)   
                VALUES(gen_random_uuid(), '%s', '%s', '%s', '%s', '%s', true);
                """.formatted(username, user.password, user.salt, email, user.roles);

        System.out.println("Run this query inside your db: ");
        System.out.println(query);
        System.exit(0);
    }

    static Credentials createCredentials(char[] password) {
        var nonce = randomNonce(16);
        var salt = Base64.getEncoder().encodeToString(nonce);
        var hashedPass = BcryptUtil.bcryptHash(String.valueOf(password), 31, nonce);
        return new Credentials(salt, hashedPass);
    }

    private static byte[] randomNonce(int length) {
        var result = new byte[length];
        try {
            SecureRandom.getInstanceStrong().nextBytes(result);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private static User createUser(String username, char[] password, String email, String roles) {
        var credentials = createCredentials(password);
        return new User(
                username,
                credentials.password(),
                credentials.salt(),
                email,
                roles,
                true);
    }

    public record Credentials(String salt, String password) {
    }

    public record User(
            @NotBlank
            @Size(min = 8, max = 50)
            String username,

            @NotBlank
            @Size(min = 8, max = 500)

            String password,

            @NotBlank
            String salt,

            @Email
            @NotBlank
            String email,

            @NotBlank
            String roles,

            Boolean enabled
    ) {
    }
}
