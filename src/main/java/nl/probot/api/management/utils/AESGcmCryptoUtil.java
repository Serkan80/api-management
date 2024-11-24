package nl.probot.api.management.utils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;

public final class AESGcmCryptoUtil {

    private static final String AES_GCM_NOPADDING = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final int AES_KEY_SIZE = 128;
    private static final int GCM_TAG_LENGTH = 16;
    private static final int GCM_IV_LENGTH = 12;

    private AESGcmCryptoUtil() {
        super();
    }

    /**
     * Encrypts the given data with the secret with AES-128.
     *
     * @param data   the data.
     * @param secret the secret.
     * @return encrypted data containing: iv, salt & data.
     */
    public static byte[] encrypt(byte[] data, char[] secret) {
        try {
            var salt = randomNonce(GCM_TAG_LENGTH);
            var iv = randomNonce(GCM_IV_LENGTH);

            var keySpec = getKey(secret, salt);
            var cipher = Cipher.getInstance(AES_GCM_NOPADDING);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

            var encryptedData = cipher.doFinal(data);

            // combine the iv, salt & data so that we can use it during decryption
            return ByteBuffer.allocate(iv.length + salt.length + encryptedData.length)
                    .put(iv)
                    .put(salt)
                    .put(encryptedData)
                    .array();
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException |
                 InvalidKeySpecException | InvalidKeyException |
                 IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException("Encryption error: ", e);
        }
    }

    /**
     * Decrypts the data with AES-128.
     * Expects the data in the following format: iv, salt & data.
     *
     * @param data   the data.
     * @param secret the secret.
     * @return the decrypted data.
     */
    public static byte[] decrypt(byte[] data, char[] secret) {
        // get back the iv and salt from the cipher text
        var bb = ByteBuffer.wrap(data);

        var iv = new byte[GCM_IV_LENGTH];
        bb.get(iv);

        var salt = new byte[GCM_TAG_LENGTH];
        bb.get(salt);

        var cipherText = new byte[bb.remaining()];
        bb.get(cipherText);

        try {
            var keySpec = getKey(secret, salt);
            var cipher = Cipher.getInstance(AES_GCM_NOPADDING);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv));

            return cipher.doFinal(cipherText);
        } catch (InvalidAlgorithmParameterException | NoSuchPaddingException | IllegalBlockSizeException |
                 NoSuchAlgorithmException | InvalidKeySpecException |
                 BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException("Decryption error: ", e);
        }
    }

    public static byte[] randomNonce(int length) {
        var result = new byte[length];
        try {
            SecureRandom.getInstanceStrong().nextBytes(result);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return result;
    }

    private static SecretKeySpec getKey(char[] secret, byte[] salt) throws NoSuchAlgorithmException, InvalidKeySpecException {
        var factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        var spec = new PBEKeySpec(secret, salt, 65536, AES_KEY_SIZE);
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), AES);
    }
}
