package com.nawawi.gcoach;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;

public class ProgressActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_progress);

        // Setup Bottom Navigation logic
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navTuner = findViewById(R.id.navTuner);
        LinearLayout navGame = findViewById(R.id.navGame);

        navHome.setOnClickListener(v -> navigateTo(DashboardActivity.class));
        navTuner.setOnClickListener(v -> navigateTo(MainActivity.class));
        navGame.setOnClickListener(v -> navigateTo(DifficultyActivity.class));
    }

    private void navigateTo(Class<?> targetActivity) {
        Intent intent = new Intent(ProgressActivity.this, targetActivity);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);

        // Remove default slide animation for smooth bottom nav feel
        overridePendingTransition(0, 0);
        finish();
    }
}