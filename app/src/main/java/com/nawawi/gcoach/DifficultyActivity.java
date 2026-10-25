package com.nawawi.gcoach;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.cardview.widget.CardView;

import java.util.Locale;

public class DifficultyActivity extends BaseActivity {

    private TextView tvEasyInfo, tvMediumInfo, tvHardInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;

        setContentView(R.layout.activity_difficulty);

        setupTopBar();
        setupBottomNav(TAB_GAME);

        // Difficulty cards
        CardView cardEasy = findViewById(R.id.cardEasy);
        CardView cardMedium = findViewById(R.id.cardMedium);
        CardView cardHard = findViewById(R.id.cardHard);

        // The small text under each title (used to show the best score)
        tvEasyInfo = findViewById(R.id.tvEasyInfo);
        tvMediumInfo = findViewById(R.id.tvMediumInfo);
        tvHardInfo = findViewById(R.id.tvHardInfo);

        // Click listeners to launch GameActivity
        cardEasy.setOnClickListener(v -> startGame("EASY"));
        cardMedium.setOnClickListener(v -> startGame("MEDIUM"));
        cardHard.setOnClickListener(v -> startGame("HARD"));
    }

    // Runs every time this screen is shown, so the score updates after a game
    @Override
    protected void onResume() {
        super.onResume();
        if (tvEasyInfo == null) return; // onCreate stopped early (not logged in)

        // Best scores come from the logged-in user's rows in SQLite
        DatabaseHelper db = DatabaseHelper.getInstance(this);
        long userId = session.getUserId();

        tvEasyInfo.setText(buildInfo("Open strings only", db.getBestScore(userId, "EASY")));
        tvMediumInfo.setText(buildInfo("Frets 1-5 included", db.getBestScore(userId, "MEDIUM")));
        tvHardInfo.setText(buildInfo("Full fretboard mastery", db.getBestScore(userId, "HARD")));
    }

    private String buildInfo(String description, int best) {
        if (best <= 0) return description + "  \u00B7  No score yet";
        return description + "  \u00B7  Best: " + String.format(Locale.US, "%,d", best);
    }

    private void startGame(String difficultyLevel) {
        Intent intent = new Intent(DifficultyActivity.this, GameActivity.class);
        // Pass the difficulty to the game screen
        intent.putExtra("DIFFICULTY", difficultyLevel);
        startActivity(intent);
        overridePendingTransition(0, 0); // instant transition
    }
}
