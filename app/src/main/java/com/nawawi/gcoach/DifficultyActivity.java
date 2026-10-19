package com.nawawi.gcoach;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class DifficultyActivity extends AppCompatActivity {

    private TextView tvEasyInfo, tvMediumInfo, tvHardInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_difficulty);

        // Difficulty cards
        CardView cardEasy = findViewById(R.id.cardEasy);
        CardView cardMedium = findViewById(R.id.cardMedium);
        CardView cardHard = findViewById(R.id.cardHard);

        // [NEW] The small text under each title (used to show the best score)
        tvEasyInfo = findViewById(R.id.tvEasyInfo);
        tvMediumInfo = findViewById(R.id.tvMediumInfo);
        tvHardInfo = findViewById(R.id.tvHardInfo);

        // Click listeners to launch GameActivity
        cardEasy.setOnClickListener(v -> startGame("EASY"));
        cardMedium.setOnClickListener(v -> startGame("MEDIUM"));
        cardHard.setOnClickListener(v -> startGame("HARD"));

        // Bottom navigation
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navTuner = findViewById(R.id.navTuner);
        LinearLayout navStats = findViewById(R.id.navStats);

        navHome.setOnClickListener(v -> navigateTo(DashboardActivity.class));
        navTuner.setOnClickListener(v -> navigateTo(MainActivity.class));
        navStats.setOnClickListener(v -> navigateTo(ProgressActivity.class));
    }

    // [NEW] Runs every time this screen is shown, so the score updates after a game
    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences("gcoach_prefs", MODE_PRIVATE);
        tvEasyInfo.setText(buildInfo("Open strings only", prefs.getInt("best_EASY", 0)));
        tvMediumInfo.setText(buildInfo("Frets 1-5 included", prefs.getInt("best_MEDIUM", 0)));
        tvHardInfo.setText(buildInfo("Full fretboard mastery", prefs.getInt("best_HARD", 0)));
    }

    private String buildInfo(String description, int best) {
        if (best <= 0) return description + "  \u00B7  No score yet";
        return description + "  \u00B7  Best: " + String.format("%,d", best);
    }

    private void startGame(String difficultyLevel) {
        Intent intent = new Intent(DifficultyActivity.this, GameActivity.class);
        // Pass the difficulty to the game screen
        intent.putExtra("DIFFICULTY", difficultyLevel);
        startActivity(intent);
        overridePendingTransition(0, 0); // instant transition
    }

    private void navigateTo(Class<?> targetActivity) {
        Intent intent = new Intent(DifficultyActivity.this, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0); // removes the slide animation for bottom nav clicks
        finish();
    }
}