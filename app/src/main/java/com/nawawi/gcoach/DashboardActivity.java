package com.nawawi.gcoach;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

public class DashboardActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

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

    private void navigateTo(Class<?> targetActivity) {
        Intent intent = new Intent(DashboardActivity.this, targetActivity);
        startActivity(intent);
        overridePendingTransition(0, 0);
        // Do not call finish() here so they can press 'Back' to return to Dashboard
    }
}