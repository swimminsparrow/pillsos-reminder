package swimminsparrow.ricordamelo.core.utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class CryptoUtilsTest {

    @Test
    public void testGenerateRandomKey() {
        String key1 = CryptoUtils.generateRandomKey();
        String key2 = CryptoUtils.generateRandomKey();

        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2);
        assertTrue(key1.length() > 10);
    }

    @Test
    public void testEncryptAndDecryptRoundTrip() {
        String key = CryptoUtils.generateRandomKey();
        String originalMessage = "EMERGENCY: User fell down at 20:30! Coordinates: 45.123,9.456";

        String ciphertext = CryptoUtils.encrypt(originalMessage, key);
        assertNotNull(ciphertext);
        assertNotEquals(originalMessage, ciphertext);

        String decrypted = CryptoUtils.decrypt(ciphertext, key);
        assertEquals(originalMessage, decrypted);
    }

    @Test
    public void testDecryptWithWrongKeyReturnsNull() {
        String keyA = CryptoUtils.generateRandomKey();
        String keyB = CryptoUtils.generateRandomKey();
        String message = "Confidential SOS payload";

        String ciphertext = CryptoUtils.encrypt(message, keyA);
        assertNotNull(ciphertext);

        String decryptedWithWrongKey = CryptoUtils.decrypt(ciphertext, keyB);
        assertNull(decryptedWithWrongKey);
    }

    @Test
    public void testCorruptedCiphertextReturnsNull() {
        String key = CryptoUtils.generateRandomKey();
        String ciphertext = CryptoUtils.encrypt("Test payload", key);
        assertNotNull(ciphertext);

        String corrupted = ciphertext.substring(0, ciphertext.length() - 5) + "ABCDE";
        String decrypted = CryptoUtils.decrypt(corrupted, key);
        assertNull(decrypted);
    }

    @Test
    public void testSha256KeyDerivation() {
        String pin1 = "123456";
        String pin2 = "654321";

        String key1 = CryptoUtils.sha256Key("pillsos_pin_salt_v1_" + pin1);
        String key1Duplicate = CryptoUtils.sha256Key("pillsos_pin_salt_v1_" + pin1);
        String key2 = CryptoUtils.sha256Key("pillsos_pin_salt_v1_" + pin2);

        assertNotNull(key1);
        assertEquals(key1, key1Duplicate);
        assertNotEquals(key1, key2);
    }

    @Test
    public void testNullInputsHandledSafely() {
        assertNull(CryptoUtils.encrypt(null, "someKey"));
        assertNull(CryptoUtils.encrypt("someText", null));
        assertNull(CryptoUtils.decrypt(null, "someKey"));
        assertNull(CryptoUtils.decrypt("someText", null));
        assertNull(CryptoUtils.sha256Key(null));
        assertNull(CryptoUtils.decodeBase64(null));
        assertNull(CryptoUtils.decodeBase64(""));
    }
}
