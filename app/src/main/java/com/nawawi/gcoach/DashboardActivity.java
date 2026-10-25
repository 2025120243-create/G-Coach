package com.nawawi.gcoach;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class DashboardActivity extends AppCompatActivity {

    private SessionManager session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Safety check: nobody gets in without logging in
        session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        setContentView(R.layout.activity_dashboard);

        // Greeting with the user's first name
        TextView welcomeText = findViewById(R.id.welcomeText);
        welcomeText.setText("Hey " + firstName(session.getDisplayName()) + ",\nReady to shred?");

        // Profile icon (top right) opens the account dialog
        ImageView ivProfile = findViewById(R.id.ivProfile);
        ivProfile.setOnClickListener(v -> showAccountDialog());

        // Cards mapping
        CardView cardTuner = findViewById(R.id.cardTuner);
        CardView cardGame = findViewById(R.id.cardGame);

        // Nav mapping
        LinearLayout navTuner = findViewById(R.id.navTuner);
        LinearLayout navGame = findViewById(R.id.navGame);
        LinearLayout navStats = findViewById(R.id.navStats);

        // Click Listeners
        cardTuner.setOnClickListener(v -> navigateTo(MainActivity.class));
        navTuner.setOnClickListener(v -> navigateTo(MainActivity.class));

        cardGame.setOnClickListener(v -> navigateTo(DifficultyActivity.class));
        navGame.setOnClickListener(v -> navigateTo(DifficultyActivity.class));

        navStats.setOnClickListener(v -> navigateTo(ProgressActivity.class));
    }

    private String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.isEmpty()) return "Guitarist";
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }

    private void showAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle(session.getDisplayName())
                .setMessage("Signed in as @" + session.getUsername())
                .setPositiveButton("Log out", (d, w) -> {
                    session.logout();
                    goToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void navigateTo(Class<?> targetActivity) {
        Intent intent = new Intent(DashboardActivity.this, targetActivity);
        startActivity(intent);
        overridePendingTransition(0, 0);
        // Do not call finish() here so they can press 'Back' to return to Dashboard
    }
}