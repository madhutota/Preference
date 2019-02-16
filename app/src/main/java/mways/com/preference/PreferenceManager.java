package mways.com.preference;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceManager {
    private static final String SECURE_PREF_NAME = "PreferenceTest";
    private static final String APP_THEME = "appTheme";
    private static final String APP_FONT_SIZE = "fontSize";

    private PreferenceManager() {
    }

    private static SharedPreferences getSecureSharedPreference(Context context) {
        return new SecurePreferences(context, "", SECURE_PREF_NAME);
    }

    public static void saveLong(Context context, String key, long value) {
        if (context != null) {
            SharedPreferences.Editor editor = getSecureSharedPreference(context).edit();
            editor.putLong(key, value);
            editor.apply();
        }
    }

    public static String getString(Context context, String key, String defaultValue) {
        if (context != null) {
            return getSecureSharedPreference(context).getString(key, defaultValue);
        } else {
            return null;
        }
    }

    public static void saveString(Context context, String key, String value) {
        if (context != null) {
            SharedPreferences.Editor editor = getSecureSharedPreference(context).edit();
            editor.putString(key, value);
            editor.apply();
        }
    }

    public static void deleteAll(Context context) {
        getSecureSharedPreference(context).edit().clear().apply();
    }



}
