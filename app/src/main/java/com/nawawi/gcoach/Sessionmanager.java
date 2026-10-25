package com.nawawi.gcoach;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Remembers who is currently logged in, so the user stays logged in
 * after closing the app until they press "Log out".
 */
public class SessionManager {

    private static final String PREF_NAME = "gcoach_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_DISPLAY_NAME = "display_name";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public void login(DatabaseHelper.User user) {
        prefs.edit()
                .putLong(KEY_USER_ID, user.id)
                .putString(KEY_USERNAME, user.username)
                .putString(KEY_DISPLAY_NAME, user.displayName)
                .apply();
    }

    /** Keeps the saved login in sync after the user edits their name on the Profile screen. */
    public void setDisplayName(String displayName) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        return prefs.getLong(KEY_USER_ID, -1) != -1;
    }

    public long getUserId() {
        return prefs.getLong(KEY_USER_ID, -1);
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, "Guitarist");
    }
}
