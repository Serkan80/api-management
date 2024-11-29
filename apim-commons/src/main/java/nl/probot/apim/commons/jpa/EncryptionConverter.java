package nl.probot.apim.commons.jpa;

import jakarta.persistence.AttributeConverter;
import nl.probot.apim.commons.crypto.CryptoUtil;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EncryptionConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(CryptoUtil.encrypt(plainText.getBytes(UTF_8), getSecret()));
    }

    @Override
    public String convertToEntityAttribute(String encrypted) {
        if (encrypted == null) {
            return null;
        }
        return new String(CryptoUtil.decrypt(Base64.getDecoder().decode(encrypted), getSecret()));
    }

    private static char[] getSecret() {
        return ConfigProvider.getConfig().getValue("encryption.key", String.class).toCharArray();
    }
}
