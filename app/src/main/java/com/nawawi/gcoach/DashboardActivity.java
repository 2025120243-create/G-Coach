package com.nawawi.gcoach;

import android.os.Bundle;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

public class DashboardActivity extends BaseActivity {

    private TextView welcomeText, tvDashStreak, tvDashSessions, tvDashAccuracy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Safety check: nobody gets in without logging in
        if (!ensureLoggedIn()) return;

        setContentView(R.layout.activity_dashboard);

        setupTopBar();               // avatar (top right) opens the Profile screen
        setupBottomNav(TAB_HOME);

        welcomeText = findViewById(R.id.welcomeText);
        tvDashStreak = findViewById(R.id.tvDashStreak);
        tvDashSessions = findViewById(R.id.tvDashSessions);
        tvDashAccuracy = findViewById(R.id.tvDashAccuracy);

        // Big cards
        CardView cardTuner = findViewById(R.id.cardTuner);
        CardView cardGame = findViewById(R.id.cardGame);
        CardView cardStats = findViewById(R.id.cardStats);
        cardTuner.setOnClickListener(v -> goToTab(TAB_TUNER));
        cardGame.setOnClickListener(v -> goToTab(TAB_GAME));
        cardStats.setOnClickListener(v -> goToTab(TAB_STATS));

        // The three little stat cards also open Stats
        findViewById(R.id.statStreakCard).setOnClickListener(v -> goToTab(TAB_STATS));
        findViewById(R.id.statGamesCard).setOnClickListener(v -> goToTab(TAB_STATS));
        findViewById(R.id.statAccuracyCard).setOnClickListener(v -> goToTab(TAB_STATS));
    }

    // Runs every time the screen is shown, so the name and numbers are always fresh
    // (for example after editing the name on the Profile screen or finishing a game)
    @Override
    protected void onResume() {
        super.onResume();
        if (welcomeText == null) return; // onCreate stopped early (not logged in)

        welcomeText.setText("Hey " + firstName(session.getDisplayName()) + ",\nReady to shred?");

        DatabaseHelper db = DatabaseHelper.getInstance(this);
        long userId = session.getUserId();

        tvDashStreak.setText(String.valueOf(db.getPlayStreak(userId)));
        tvDashSessions.setText(String.valueOf(db.getSessionCount(userId)));
        int avg = db.getAverageAccuracy(userId);
        tvDashAccuracy.setText(avg < 0 ? "--" : avg + "%");
    }

    private String firstName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        if (trimmed.isEmpty()) return "Guitarist";
        int space = trimmed.indexOf(' ');
        return space > 0 ? trimmed.substring(0, space) : trimmed;
    }
}
