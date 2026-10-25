package com.nawawi.gcoach;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The user's profile: who is logged in, quick stats, and the account actions
 * (edit name, change password, reset progress, log out, delete account).
 *
 * Opened from the avatar in the top bar of every main screen.
 */
public class ProfileActivity extends BaseActivity {

    private static final int MIN_PASSWORD_LENGTH = 6; // same rule as RegisterActivity

    private TextView tvAvatarLarge, tvProfileName, tvProfileUsername, tvMemberSince;
    private TextView tvProfileGames, tvProfileAccuracy, tvProfileStreak, tvProfileNotes;

    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;

        setContentView(R.layout.activity_profile);
        db = DatabaseHelper.getInstance(this);

        // Back arrow on the left, no avatar (we are already on the profile)
        setupTopBar("My Profile", R.drawable.ic_back, "Back", v -> finish(), false);

        tvAvatarLarge = findViewById(R.id.tvAvatarLarge);
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileUsername = findViewById(R.id.tvProfileUsername);
        tvMemberSince = findViewById(R.id.tvMemberSince);
        tvProfileGames = findViewById(R.id.tvProfileGames);
        tvProfileAccuracy = findViewById(R.id.tvProfileAccuracy);
        tvProfileStreak = findViewById(R.id.tvProfileStreak);
        tvProfileNotes = findViewById(R.id.tvProfileNotes);

        findViewById(R.id.rowEditName).setOnClickListener(v -> showEditNameDialog());
        findViewById(R.id.rowChangePassword).setOnClickListener(v -> showChangePasswordDialog());
        findViewById(R.id.rowResetProgress).setOnClickListener(v -> confirmResetProgress());
        findViewById(R.id.rowLogout).setOnClickListener(v -> confirmLogout());
        findViewById(R.id.rowDeleteAccount).setOnClickListener(v -> showDeleteAccountDialog());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (db == null) return; // onCreate stopped early (not logged in)
        loadProfile();
    }

    // =====================================================================
    // Show the data
    // =====================================================================
    private void loadProfile() {
        long userId = session.getUserId();
        String name = session.getDisplayName();

        tvAvatarLarge.setText(initialOf(name));
        tvProfileName.setText(name);
        tvProfileUsername.setText("@" + session.getUsername());

        long created = db.getCreatedAt(userId);
        if (created > 0) {
            tvMemberSince.setText("Member since "
                    + new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date(created)));
        } else {
            tvMemberSince.setText("");
        }

        tvProfileGames.setText(String.valueOf(db.getSessionCount(userId)));

        int avg = db.getAverageAccuracy(userId);
        tvProfileAccuracy.setText(avg < 0 ? "--" : avg + "%");

        tvProfileStreak.setText(String.valueOf(db.getPlayStreak(userId)));

        int hits = db.getTotalHits(userId);
        tvProfileNotes.setText(hits == 0
                ? "No notes matched yet. Play a game to get started!"
                : String.format(Locale.US, "%,d notes matched so far", hits));
    }

    // =====================================================================
    // Edit display name
    // =====================================================================
    private void showEditNameDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_edit_name, null);
        TextInputLayout til = view.findViewById(R.id.tilDialogName);
        TextInputEditText et = view.findViewById(R.id.etDialogName);
        et.setText(session.getDisplayName());
        if (et.getText() != null) et.setSelection(et.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Edit display name")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", null) // set below so the dialog can stay open on errors
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String name = text(et).trim();
                    if (name.length() < 2) {
                        til.setError("Enter your name (at least 2 characters)");
                        return;
                    }
                    if (name.length() > 30) {
                        til.setError("Keep it under 30 characters");
                        return;
                    }
                    if (db.updateDisplayName(session.getUserId(), name)) {
                        session.setDisplayName(name);
                        loadProfile();
                        Toast.makeText(this, "Name updated", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } else {
                        til.setError("Could not save. Please try again");
                    }
                }));
        showWithKeyboard(dialog);
    }

    // =====================================================================
    // Change password
    // =====================================================================
    private void showChangePasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        TextInputLayout tilCurrent = view.findViewById(R.id.tilCurrentPassword);
        TextInputLayout tilNew = view.findViewById(R.id.tilNewPassword);
        TextInputLayout tilConfirm = view.findViewById(R.id.tilConfirmPassword);
        TextInputEditText etCurrent = view.findViewById(R.id.etCurrentPassword);
        TextInputEditText etNew = view.findViewById(R.id.etNewPassword);
        TextInputEditText etConfirm = view.findViewById(R.id.etConfirmPassword);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Change password")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Update", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    tilCurrent.setError(null);
                    tilNew.setError(null);
                    tilConfirm.setError(null);

                    String current = text(etCurrent);
                    String newPass = text(etNew);
                    String confirm = text(etConfirm);

                    if (current.isEmpty()) {
                        tilCurrent.setError("Enter your current password");
                        return;
                    }
                    if (newPass.length() < MIN_PASSWORD_LENGTH) {
                        tilNew.setError("At least " + MIN_PASSWORD_LENGTH + " characters");
                        return;
                    }
                    if (!newPass.equals(confirm)) {
                        tilConfirm.setError("Passwords do not match");
                        return;
                    }
                    if (!db.changePassword(session.getUserId(), current, newPass)) {
                        tilCurrent.setError("Wrong current password");
                        return;
                    }
                    Toast.makeText(this, "Password updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                }));
        showWithKeyboard(dialog);
    }

    // =====================================================================
    // Reset progress
    // =====================================================================
    private void confirmResetProgress() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reset my progress?")
                .setMessage("This deletes all your saved games, scores and streaks. "
                        + "Your account stays. This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Reset", (d, w) -> {
                    db.clearSessions(session.getUserId());
                    loadProfile();
                    Toast.makeText(this, "Progress reset", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    // =====================================================================
    // Log out
    // =====================================================================
    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out?")
                .setMessage("You are signed in as @" + session.getUsername())
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Log out", (d, w) -> {
                    session.logout();
                    goToLogin();
                })
                .show();
    }

    // =====================================================================
    // Delete account (asks for the password first)
    // =====================================================================
    private void showDeleteAccountDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_confirm_password, null);
        TextInputLayout til = view.findViewById(R.id.tilConfirmDelete);
        TextInputEditText et = view.findViewById(R.id.etConfirmDelete);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Delete account?")
                .setMessage("Your account and all saved scores will be permanently deleted. "
                        + "Enter your password to confirm.")
                .setView(view)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    til.setError(null);
                    long userId = session.getUserId();
                    if (!db.verifyPassword(userId, text(et))) {
                        til.setError("Wrong password");
                        return;
                    }
                    db.deleteUser(userId);
                    session.logout();
                    Toast.makeText(this, "Account deleted", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    goToLogin();
                }));
        showWithKeyboard(dialog);
    }

    // =====================================================================
    // Small helpers
    // =====================================================================
    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private void showWithKeyboard(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }
}
