package mways.com.preference;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.security.KeyPairGeneratorSpec;
import android.text.TextUtils;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.security.auth.x500.X500Principal;

public class SecurePreferences implements SharedPreferences {

    private static final int ORIGINAL_ITERATION_COUNT = 10000;
    private SharedPreferences sharedPreferences;
    private AesCbcWithIntegrity.SecretKeys keys;
    private String salt;
    private String sharedPrefFilename;


    /**
     * User password defaults to app generated password that's stores obfucated with the other preference values. Also this uses the Default shared pref file
     *
     * @param context should be ApplicationContext not Activity
     */
    public SecurePreferences(Context context) {
        this(context, "", null);
    }


    /**
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     */
    public SecurePreferences(Context context, int iterationCount) {
        this(context, "", null, iterationCount);
    }

    /**
     * @param context            should be ApplicationContext not Activity
     * @param password           user password/code used to generate encryption key.
     * @param sharedPrefFilename name of the shared pref file. If null use the default shared prefs
     */
    public SecurePreferences(Context context, final String password, final String sharedPrefFilename) {
        this(context, password, null, sharedPrefFilename, ORIGINAL_ITERATION_COUNT);
    }


    /**
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     */
    public SecurePreferences(Context context, final String password, final String sharedPrefFilename, int iterationCount) {
        this(context, password, null, sharedPrefFilename, iterationCount);
    }


    /**
     * @param context            should be ApplicationContext not Activity
     * @param secretKey          that you've generated
     * @param sharedPrefFilename name of the shared pref file. If null use the default shared prefs
     */
    public SecurePreferences(Context context, final AesCbcWithIntegrity.SecretKeys secretKey, final String sharedPrefFilename) {
        this(context, null, null, sharedPrefFilename, 0);
    }

    /**
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     */
    public SecurePreferences(Context context, String password, final String salt, final String sharedPrefFilename, int iterationCount) {
        if (sharedPreferences == null) {
            sharedPreferences = getSharedPreferenceFile(context, sharedPrefFilename);
        }
        this.salt = salt;
        if (TextUtils.isEmpty(password)) {
            try {
                final String key = generateAesKeyName(context, iterationCount);
                KeyStore keystore = KeyStore.getInstance("AndroidKeyStore");
                try {
                    keystore.load(null);
                } catch (IOException ignored) {
                }
                String keyAsString = sharedPreferences.getString(key, null);
                if (keyAsString == null) {
                    keys = AesCbcWithIntegrity.generateKey();
                    createNewKeys(context, iterationCount, keystore);
                    sharedPreferences.edit().putString(key, encryptKey(key, keystore, keys.toString())).commit();
                } else {
                    keys = AesCbcWithIntegrity.keys(decryptKey(key, keystore, keyAsString));
                }

                if (keys == null) {
                    throw new GeneralSecurityException("Problem generating Key");
                }

            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        } else {
            try {
                final byte[] saltBytes = getSalt(context).getBytes();
                keys = AesCbcWithIntegrity.generateKeyFromPassword(password, saltBytes, iterationCount);

                if (keys == null) {
                    throw new GeneralSecurityException("Problem generating Key From Password");
                }
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException(e);
            }
        }

    }


    /**
     * Gets the hardware serial number of this device.
     *
     * @return serial number or Settings.Secure.ANDROID_ID if not available.
     */
    @SuppressLint("HardwareIds")
    private static String getDeviceSerialNumber(Context context) {
        // We're using the Reflection API because Build.SERIAL is only available
        // since API Level 9 (Gingerbread, Android 2.3).
        try {
            String deviceSerial = (String) Build.class.getField("SERIAL").get(
                    null);
            if (TextUtils.isEmpty(deviceSerial)) {
                return Settings.Secure.getString(
                        context.getContentResolver(),
                        Settings.Secure.ANDROID_ID);
            } else {
                return deviceSerial;
            }
        } catch (Exception ignored) {
            // Fall back  to Android_ID
            return Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        }
    }

    /**
     * The Pref keys must be same each time so we're using a hash to obscure the stored value
     *
     * @param prefKey
     * @return SHA-256 Hash of the preference key
     */
    public static String hashPrefKey(String prefKey) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = prefKey.getBytes("UTF-8");
            digest.update(bytes, 0, bytes.length);

            return Base64.encodeToString(digest.digest(), AesCbcWithIntegrity.BASE64_FLAGS);

        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
        }
        return null;
    }

    /**
     * if a prefFilename is not defined the getDefaultSharedPreferences is used.
     *
     * @param context should be ApplicationContext not Activity
     * @return
     */
    private SharedPreferences getSharedPreferenceFile(Context context, String prefFilename) {
        this.sharedPrefFilename = prefFilename;

        if (TextUtils.isEmpty(prefFilename)) {
            return PreferenceManager
                    .getDefaultSharedPreferences(context);
        } else {
            return context.getSharedPreferences(prefFilename, Context.MODE_PRIVATE);
        }
    }

    /**
     * nulls in memory keys
     */
    public void destroyKeys() {
        keys = null;
    }

    /**
     * Uses device and application values to generate the pref key for the encryption key
     *
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     * @return String to be used as the AESkey Pref key
     * @throws GeneralSecurityException if something goes wrong in generation
     */
    private String generateAesKeyName(Context context, int iterationCount) throws GeneralSecurityException {
        final String password = context.getPackageName();
        final byte[] salt = getSalt(context).getBytes();
        AesCbcWithIntegrity.SecretKeys generatedKeyName = AesCbcWithIntegrity.generateKeyFromPassword(password, salt, iterationCount);

        return hashPrefKey(generatedKeyName.toString());
    }

    /**
     * Gets the salt value
     *
     * @param context used for accessing hardware serial number of this device in case salt is not set
     * @return
     */
    private String getSalt(Context context) {
        if (TextUtils.isEmpty(this.salt)) {
            return getDeviceSerialNumber(context);
        } else {
            return this.salt;
        }
    }

    private String encrypt(String cleartext) {
        if (TextUtils.isEmpty(cleartext)) {
            return cleartext;
        }
        try {
            return AesCbcWithIntegrity.encrypt(cleartext, keys).toString();
        } catch (GeneralSecurityException e) {
            return null;
        } catch (UnsupportedEncodingException ignored) {
        }
        return null;
    }

    /**
     * @param ciphertext
     * @return decrypted plain text, unless decryption fails, in which case null
     */
    private String decrypt(final String ciphertext) {
        if (TextUtils.isEmpty(ciphertext)) {
            return ciphertext;
        }
        try {
            AesCbcWithIntegrity.CipherTextIvMac cipherTextIvMac = new AesCbcWithIntegrity.CipherTextIvMac(ciphertext);

            return AesCbcWithIntegrity.decryptString(cipherTextIvMac, keys);
        } catch (GeneralSecurityException | UnsupportedEncodingException ignored) {

        }
        return null;
    }

    /**
     * @return map of with decrypted values (excluding the key if present)
     */
    @Override
    public Map<String, String> getAll() {
        //wont be null as per http://androidxref.com/5.1.0_r1/xref/frameworks/base/core/java/android/app/SharedPreferencesImpl.java
        final Map<String, ?> encryptedMap = sharedPreferences.getAll();
        final Map<String, String> decryptedMap = new HashMap<String, String>(
                encryptedMap.size());
        for (Map.Entry<String, ?> entry : encryptedMap.entrySet()) {
            try {
                Object cipherText = entry.getValue();
                //don't include the key
                if (cipherText != null && !cipherText.equals(keys.toString())) {
                    //the prefs should all be strings
                    decryptedMap.put(entry.getKey(),
                            decrypt(cipherText.toString()));
                }
            } catch (Exception e) {
                // Ignore issues that unencrypted values and use instead raw cipher text string
                decryptedMap.put(entry.getKey(),
                        entry.getValue().toString());
            }
        }
        return decryptedMap;
    }

    @Override
    public String getString(String key, String defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);

        String decryptedValue = decrypt(encryptedValue);
        if (encryptedValue != null && decryptedValue != null) {
            return decryptedValue;
        } else {
            return defaultValue;
        }
    }

    /**
     * Added to get a values as as it can be useful to store values that are
     * already encrypted and encoded
     *
     * @param key          pref key
     * @param defaultValue
     * @return Encrypted value of the key or the defaultValue if no value exists
     */
    public String getEncryptedString(String key, String defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);
        return (encryptedValue != null) ? encryptedValue : defaultValue;
    }

    @Override
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public Set<String> getStringSet(String key, Set<String> defaultValues) {
        final Set<String> encryptedSet = sharedPreferences.getStringSet(
                SecurePreferences.hashPrefKey(key), null);
        if (encryptedSet == null) {
            return defaultValues;
        }
        final Set<String> decryptedSet = new HashSet<String>(
                encryptedSet.size());
        for (String encryptedValue : encryptedSet) {
            decryptedSet.add(decrypt(encryptedValue));
        }
        return decryptedSet;
    }

    @Override
    public int getInt(String key, int defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(decrypt(encryptedValue));
        } catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public long getLong(String key, long defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(decrypt(encryptedValue));
        } catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public float getFloat(String key, float defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(decrypt(encryptedValue));
        } catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public boolean getBoolean(String key, boolean defaultValue) {
        final String encryptedValue = sharedPreferences.getString(
                SecurePreferences.hashPrefKey(key), null);
        if (encryptedValue == null) {
            return defaultValue;
        }
        try {
            return Boolean.parseBoolean(decrypt(encryptedValue));
        } catch (NumberFormatException e) {
            throw new ClassCastException(e.getMessage());
        }
    }

    @Override
    public boolean contains(String key) {
        return sharedPreferences.contains(SecurePreferences.hashPrefKey(key));
    }


    /**
     * Cycle through the unencrypt all the current prefs to mem cache, clear, then encypt with key generated from new password.
     * This method can be used if switching from the generated key to a key derived from user password
     * <p>
     * Note: the pref keys will remain the same as they are SHA256 hashes.
     *
     * @param newPassword
     * @param context        should be ApplicationContext not Activity
     * @param iterationCount The iteration count for the keys generation
     */
    @SuppressLint("CommitPrefEdits")
    public void handlePasswordChange(String newPassword, Context context, int iterationCount) throws GeneralSecurityException {

        final byte[] salt = getSalt(context).getBytes();
        AesCbcWithIntegrity.SecretKeys newKey = AesCbcWithIntegrity.generateKeyFromPassword(newPassword, salt, iterationCount);

        Map<String, ?> allOfThePrefs = sharedPreferences.getAll();
        Map<String, String> unencryptedPrefs = new HashMap<String, String>(allOfThePrefs.size());
        //iterate through the current prefs unencrypting each one
        for (String prefKey : allOfThePrefs.keySet()) {
            Object prefValue = allOfThePrefs.get(prefKey);
            if (prefValue instanceof String) {
                //all the encrypted values will be Strings
                final String prefValueString = (String) prefValue;
                final String plainTextPrefValue = decrypt(prefValueString);
                unencryptedPrefs.put(prefKey, plainTextPrefValue);
            }
        }

        //destroy and clear the current pref file
        destroyKeys();

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.apply();

        //refresh the sharedPreferences object ref: I found it was retaining old ref/values
        sharedPreferences = null;
        sharedPreferences = getSharedPreferenceFile(context, sharedPrefFilename);

        //assign new key
        this.keys = newKey;

        SharedPreferences.Editor updatedEditor = sharedPreferences.edit();

        //iterate through the unencryptedPrefs encrypting each one with new key
        Iterator<String> unencryptedPrefsKeys = unencryptedPrefs.keySet().iterator();
        while (unencryptedPrefsKeys.hasNext()) {
            String prefKey = unencryptedPrefsKeys.next();
            String prefPlainText = unencryptedPrefs.get(prefKey);
            updatedEditor.putString(prefKey, encrypt(prefPlainText));
        }
        updatedEditor.apply();
    }

    public void handlePasswordChange(String newPassword, Context context) throws GeneralSecurityException {
        handlePasswordChange(newPassword, context, ORIGINAL_ITERATION_COUNT);
    }


    @Override
    public Editor edit() {
        return new Editor();
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(
            final OnSharedPreferenceChangeListener listener) {
        sharedPreferences
                .registerOnSharedPreferenceChangeListener(listener);
    }

    /**
     * @param listener    OnSharedPreferenceChangeListener
     * @param decryptKeys Callbacks receive the "key" parameter decrypted
     */
    public void registerOnSharedPreferenceChangeListener(
            final OnSharedPreferenceChangeListener listener, boolean decryptKeys) {

        if (!decryptKeys) {
            registerOnSharedPreferenceChangeListener(listener);
        }
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(
            OnSharedPreferenceChangeListener listener) {
        sharedPreferences
                .unregisterOnSharedPreferenceChangeListener(listener);
    }

    public void createNewKeys(Context context, int iterationCount, KeyStore keyStore) throws GeneralSecurityException {
        String alias = generateAesKeyName(context, iterationCount);
        try {
            // Create new key if needed
            if (!keyStore.containsAlias(alias)) {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 5);
                KeyPairGeneratorSpec spec = null;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    spec = new KeyPairGeneratorSpec.Builder(context)
                            .setAlias(alias)
                            .setSubject(new X500Principal("CN=Sample Name, O=" + getSalt(context)))
                            .setSerialNumber(BigInteger.ONE)
                            .setStartDate(start.getTime())
                            .setEndDate(end.getTime())
                            .build();
                }
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                    generator.initialize(spec);
                }
                KeyPair keyPair = generator.generateKeyPair();
            }
        } catch (Exception e) {
            return;
        }
    }

    public String encryptKey(String alias, KeyStore keyStore, String encryptionValue) {
        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();
            Cipher input = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
            input.init(Cipher.ENCRYPT_MODE, publicKey);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(
                    outputStream, input);
            cipherOutputStream.write(encryptionValue.getBytes("UTF-8"));
            cipherOutputStream.close();

            byte[] vals = outputStream.toByteArray();
            return Base64.encodeToString(vals, Base64.DEFAULT);
        } catch (Exception ignored) {
            return null;
        }
    }

    public String decryptKey(String alias, KeyStore keyStore, String encyptedKey) {
        try {
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            Cipher output = Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL");
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                output = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            }
            output.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());

            CipherInputStream cipherInputStream = new CipherInputStream(
                    new ByteArrayInputStream(Base64.decode(encyptedKey, Base64.DEFAULT)), output);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte) nextByte);
            }

            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = values.get(i).byteValue();
            }

            String decypted = new String(bytes, 0, bytes.length, "UTF-8");
            return decypted;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wrapper for Android's {@link android.content.SharedPreferences.Editor}.
     * <p>
     * Used for modifying values in a {@link SecurePreferences} object. All
     * changes you make in an editor are batched, and not copied back to the
     * original {@link SecurePreferences} until you call {@link #commit()} or
     * {@link #apply()}.
     */
    public final class Editor implements SharedPreferences.Editor {
        private SharedPreferences.Editor mEditor;

        /**
         * Constructor.
         */
        private Editor() {
            mEditor = sharedPreferences.edit();
        }

        @Override
        public SharedPreferences.Editor putString(String key, String value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key),
                    encrypt(value));
            return this;
        }

        /**
         * This is useful for storing values that have be encrypted by something
         * else or for testing
         *
         * @param key   - encrypted as usual
         * @param value will not be encrypted
         * @return
         */
        public SharedPreferences.Editor putUnencryptedString(String key,
                                                             String value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key), value);
            return this;
        }

        @Override
        @TargetApi(Build.VERSION_CODES.HONEYCOMB)
        public SharedPreferences.Editor putStringSet(String key,
                                                     Set<String> values) {
            final Set<String> encryptedValues = new HashSet<String>(
                    values.size());
            for (String value : values) {
                encryptedValues.add(encrypt(value));
            }
            mEditor.putStringSet(SecurePreferences.hashPrefKey(key),
                    encryptedValues);
            return this;
        }

        @Override
        public SharedPreferences.Editor putInt(String key, int value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key),
                    encrypt(Integer.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putLong(String key, long value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key),
                    encrypt(Long.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putFloat(String key, float value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key),
                    encrypt(Float.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor putBoolean(String key, boolean value) {
            mEditor.putString(SecurePreferences.hashPrefKey(key),
                    encrypt(Boolean.toString(value)));
            return this;
        }

        @Override
        public SharedPreferences.Editor remove(String key) {
            mEditor.remove(SecurePreferences.hashPrefKey(key));
            return this;
        }

        @Override
        public SharedPreferences.Editor clear() {
            mEditor.clear();
            return this;
        }

        @Override
        public boolean commit() {
            return mEditor.commit();
        }

        @Override
        @TargetApi(Build.VERSION_CODES.GINGERBREAD)
        public void apply() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                mEditor.apply();
            } else {
                commit();
            }
        }
    }
}
