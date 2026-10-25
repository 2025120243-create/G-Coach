package com.nawawi.gcoach;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Local SQLite database for G-Coach (fully offline).
 *
 * Tables:
 *   users    - one row per registered account (password is stored as a salted PBKDF2 hash, never plain text)
 *   sessions - one row per finished Note-Match game, linked to a user by user_id
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "gcoach.db";
    private static final int DB_VERSION = 1;

    private static final int HASH_ITERATIONS = 10000;
    private static final int HASH_BITS = 256;

    private static DatabaseHelper instance;

    public static synchronized DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DatabaseHelper(context.getApplicationContext());
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // ---------------------------------------------------------------------
    // Small data holders
    // ---------------------------------------------------------------------
    public static class User {
        public final long id;
        public final String username;
        public final String displayName;

        public User(long id, String username, String displayName) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
        }
    }

    public static class SessionRecord {
        public final String difficulty;
        public final int score;
        public final int accuracy;
        public final long playedAt;

        SessionRecord(String difficulty, int score, int accuracy, long playedAt) {
            this.difficulty = difficulty;
            this.score = score;
            this.accuracy = accuracy;
            this.playedAt = playedAt;
        }
    }

    // ---------------------------------------------------------------------
    // Schema
    // ---------------------------------------------------------------------
    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "username TEXT NOT NULL UNIQUE COLLATE NOCASE, "
                + "display_name TEXT NOT NULL, "
                + "password_hash TEXT NOT NULL, "
                + "salt TEXT NOT NULL, "
                + "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE, "
                + "difficulty TEXT NOT NULL, "
                + "score INTEGER NOT NULL, "
                + "accuracy INTEGER NOT NULL, "
                + "hits INTEGER NOT NULL, "
                + "best_streak INTEGER NOT NULL, "
                + "played_at INTEGER NOT NULL)");

        db.execSQL("CREATE INDEX idx_sessions_user ON sessions(user_id, played_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to migrate yet (DB_VERSION is still 1).
        // When you change the schema later, bump DB_VERSION and add ALTER TABLE statements here.
    }

    // ---------------------------------------------------------------------
    // Accounts
    // ---------------------------------------------------------------------
    public boolean usernameExists(String username) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT 1 FROM users WHERE username = ?",
                new String[]{username.trim()});
        try {
            return c.moveToFirst();
        } finally {
            c.close();
        }
    }

    /** Creates a new account. Returns the new user id, or -1 if the username is already taken. */
    public long registerUser(String username, String displayName, String password) {
        username = username.trim();
        if (usernameExists(username)) return -1;

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        ContentValues v = new ContentValues();
        v.put("username", username);
        v.put("display_name", displayName.trim());
        v.put("password_hash", hashPassword(password, salt));
        v.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        v.put("created_at", System.currentTimeMillis());
        return getWritableDatabase().insert("users", null, v);
    }

    /** Returns the User if username + password are correct, otherwise null. */
    public User loginUser(String username, String password) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, username, display_name, password_hash, salt FROM users WHERE username = ?",
                new String[]{username.trim()});
        try {
            if (!c.moveToFirst()) return null;

            byte[] salt = Base64.decode(c.getString(4), Base64.NO_WRAP);
            String expected = c.getString(3);
            String actual = hashPassword(password, salt);

            // constant-time comparison
            if (!MessageDigest.isEqual(expected.getBytes(), actual.getBytes())) return null;

            return new User(c.getLong(0), c.getString(1), c.getString(2));
        } finally {
            c.close();
        }
    }

    private static String hashPassword(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, HASH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            return Base64.encodeToString(factory.generateSecret(spec).getEncoded(), Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    // ---------------------------------------------------------------------
    // Game progress (always per user)
    // ---------------------------------------------------------------------
    public void saveSession(long userId, String difficulty, int score, int accuracy,
                            int hits, int bestStreak) {
        ContentValues v = new ContentValues();
        v.put("user_id", userId);
        v.put("difficulty", difficulty);
        v.put("score", score);
        v.put("accuracy", accuracy);
        v.put("hits", hits);
        v.put("best_streak", bestStreak);
        v.put("played_at", System.currentTimeMillis());
        getWritableDatabase().insert("sessions", null, v);
    }

    public int getBestScore(long userId, String difficulty) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT MAX(score) FROM sessions WHERE user_id = ? AND difficulty = ?",
                new String[]{String.valueOf(userId), difficulty});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int getSessionCount(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM sessions WHERE user_id = ?",
                new String[]{String.valueOf(userId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Average accuracy (0-100) over all games, or -1 if the user has not played yet. */
    public int getAverageAccuracy(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT AVG(accuracy), COUNT(*) FROM sessions WHERE user_id = ?",
                new String[]{String.valueOf(userId)});
        try {
            if (!c.moveToFirst() || c.getInt(1) == 0) return -1;
            return Math.round(c.getFloat(0));
        } finally {
            c.close();
        }
    }

    /** Newest session first. */
    public List<SessionRecord> getRecentSessions(long userId, int limit) {
        List<SessionRecord> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT difficulty, score, accuracy, played_at FROM sessions "
                        + "WHERE user_id = ? ORDER BY played_at DESC LIMIT ?",
                new String[]{String.valueOf(userId), String.valueOf(limit)});
        try {
            while (c.moveToNext()) {
                list.add(new SessionRecord(c.getString(0), c.getInt(1), c.getInt(2), c.getLong(3)));
            }
        } finally {
            c.close();
        }
        return list;
    }

    /**
     * Number of days in a row (ending today or yesterday) on which the user finished at least one game.
     */
    public int getPlayStreak(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT DISTINCT date(played_at / 1000, 'unixepoch', 'localtime') AS d "
                        + "FROM sessions WHERE user_id = ? ORDER BY d DESC",
                new String[]{String.valueOf(userId)});
        try {
            if (!c.moveToFirst()) return 0;

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Calendar cal = Calendar.getInstance();

            // If the latest game was not today, the streak can only continue from yesterday
            if (!fmt.format(cal.getTime()).equals(c.getString(0))) {
                cal.add(Calendar.DAY_OF_YEAR, -1);
                if (!fmt.format(cal.getTime()).equals(c.getString(0))) return 0;
            }

            int streak = 0;
            do {
                if (fmt.format(cal.getTime()).equals(c.getString(0))) {
                    streak++;
                    cal.add(Calendar.DAY_OF_YEAR, -1);
                } else {
                    break;
                }
            } while (c.moveToNext());
            return streak;
        } finally {
            c.close();
        }
    }

    // ---------------------------------------------------------------------
    // Profile (used by ProfileActivity)
    // ---------------------------------------------------------------------

    /** When the account was created (milliseconds), or 0 if the user is not found. */
    public long getCreatedAt(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT created_at FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)});
        try {
            return c.moveToFirst() ? c.getLong(0) : 0L;
        } finally {
            c.close();
        }
    }

    /** Total number of notes the user has matched correctly across all games. */
    public int getTotalHits(long userId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(hits), 0) FROM sessions WHERE user_id = ?",
                new String[]{String.valueOf(userId)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    /** Changes the name shown in the app. Returns true if a row was updated. */
    public boolean updateDisplayName(long userId, String newName) {
        ContentValues v = new ContentValues();
        v.put("display_name", newName.trim());
        return getWritableDatabase().update("users", v, "id = ?",
                new String[]{String.valueOf(userId)}) > 0;
    }

    /** True if this is the correct password for the given user id. */
    public boolean verifyPassword(long userId, String password) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT password_hash, salt FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)});
        try {
            if (!c.moveToFirst()) return false;
            byte[] salt = Base64.decode(c.getString(1), Base64.NO_WRAP);
            String expected = c.getString(0);
            String actual = hashPassword(password, salt);
            // constant-time comparison
            return MessageDigest.isEqual(expected.getBytes(), actual.getBytes());
        } finally {
            c.close();
        }
    }

    /**
     * Changes the password after checking the old one.
     * Returns false if the old password is wrong. A fresh salt is generated every time.
     */
    public boolean changePassword(long userId, String oldPassword, String newPassword) {
        if (!verifyPassword(userId, oldPassword)) return false;

        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);

        ContentValues v = new ContentValues();
        v.put("password_hash", hashPassword(newPassword, salt));
        v.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP));
        return getWritableDatabase().update("users", v, "id = ?",
                new String[]{String.valueOf(userId)}) > 0;
    }

    /** Deletes every saved game of this user (the account itself stays). */
    public void clearSessions(long userId) {
        getWritableDatabase().delete("sessions", "user_id = ?",
                new String[]{String.valueOf(userId)});
    }

    /** Deletes the account and all of its saved games. */
    public void deleteUser(long userId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("sessions", "user_id = ?", new String[]{String.valueOf(userId)});
            db.delete("users", "id = ?", new String[]{String.valueOf(userId)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
