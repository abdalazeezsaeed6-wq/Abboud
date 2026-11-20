package com.abualaz.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureConfigStore {
    private static final String PREFS = "abboud_secure_settings";
    private static final String KEY_ALIAS = "AbboudCloudKeyV1";
    private static final String ENC_KEY = "api_key_enc";
    private static final String IV_KEY = "api_key_iv";

    private final SharedPreferences prefs;

    public SecureConfigStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public String getEndpoint() {
        return prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions");
    }

    public String getModel() {
        return prefs.getString("model", "");
    }

    public boolean isCloudEnabled() {
        return prefs.getBoolean("cloud_enabled", false);
    }

    public void save(String endpoint, String model, String apiKey, boolean enabled) throws Exception {
        if (endpoint == null || !endpoint.trim().startsWith("https://")) {
            throw new IllegalArgumentException("يجب أن يبدأ رابط الخدمة بـ https://");
        }
        if (model == null || model.trim().isEmpty()) {
            throw new IllegalArgumentException("اكتب اسم النموذج الذي يدعمه مزود الخدمة.");
        }
        SharedPreferences.Editor editor = prefs.edit()
                .putString("endpoint", endpoint.trim())
                .putString("model", model.trim())
                .putBoolean("cloud_enabled", enabled);
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            byte[] iv = new byte[12];
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));
            editor.putString(ENC_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP));
            editor.putString(IV_KEY, Base64.encodeToString(iv, Base64.NO_WRAP));
        }
        editor.apply();
    }

    public String getApiKey() throws Exception {
        String encrypted = prefs.getString(ENC_KEY, null);
        String ivText = prefs.getString(IV_KEY, null);
        if (encrypted == null || ivText == null) return "";
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(),
                new GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP)));
        byte[] clear = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
        return new String(clear, StandardCharsets.UTF_8);
    }

    public void clearApiKey() {
        prefs.edit().remove(ENC_KEY).remove(IV_KEY).putBoolean("cloud_enabled", false).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) {
            return (SecretKey) store.getKey(KEY_ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
