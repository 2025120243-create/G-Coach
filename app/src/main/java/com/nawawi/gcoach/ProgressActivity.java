package com.nawawi.gcoach;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ProgressActivity extends BaseActivity {

    private TextView tvUser, tvStreak, tvSessions, tvAccuracy, tvChartLabel;
    private TextView tvBestEasy, tvBestMedium, tvBestHard, tvNoSessions;
    private LinearLayout chartContainer, recentContainer;
    private View btnPlayNow;

    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;

        setContentView(R.layout.activity_progress);

        db = DatabaseHelper.getInstance(this);

        setupTopBar();               // avatar (top right) opens the Profile screen
        setupBottomNav(TAB_STATS);

        tvUser = findViewById(R.id.tvUser);
        tvStreak = findViewById(R.id.tvStreak);
        tvSessions = findViewById(R.id.tvSessions);
        tvAccuracy = findViewById(R.id.tvAccuracy);
        tvChartLabel = findViewById(R.id.tvChartLabel);
        tvBestEasy = findViewById(R.id.tvBestEasy);
        tvBestMedium = findViewById(R.id.tvBestMedium);
        tvBestHard = findViewById(R.id.tvBestHard);
        tvNoSessions = findViewById(R.id.tvNoSessions);
        chartContainer = findViewById(R.id.chartContainer);
        recentContainer = findViewById(R.id.recentContainer);

        // Empty state: jump straight to the game picker
        btnPlayNow = findViewById(R.id.btnPlayNow);
        btnPlayNow.setOnClickListener(v -> goToTab(TAB_GAME));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (db == null) return; // onCreate stopped early (not logged in)
        loadStats();
    }

    private void loadStats() {
        long userId = session.getUserId();

        tvUser.setText("Progress for " + session.getDisplayName());

        int streak = db.getPlayStreak(userId);
        tvStreak.setText(streak + (streak == 1 ? " Day" : " Days"));

        tvSessions.setText(db.getSessionCount(userId) + " Total");

        int avg = db.getAverageAccuracy(userId);
        tvAccuracy.setText(avg < 0 ? "--" : avg + "%");

        tvBestEasy.setText(formatBest(db.getBestScore(userId, "EASY")));
        tvBestMedium.setText(formatBest(db.getBestScore(userId, "MEDIUM")));
        tvBestHard.setText(formatBest(db.getBestScore(userId, "HARD")));

        List<DatabaseHelper.SessionRecord> recent = db.getRecentSessions(userId, 6);
        buildChart(recent);
        buildRecentList(recent);
    }

    private String formatBest(int best) {
        return best <= 0 ? "--" : String.format(Locale.US, "%,d pts", best);
    }

    // Bar chart of accuracy for the last (up to) 6 sessions, oldest on the left
    private void buildChart(List<DatabaseHelper.SessionRecord> recent) {
        chartContainer.removeAllViews();

        if (recent.isEmpty()) {
            tvChartLabel.setText("Play a game to see your chart");
            return;
        }
        tvChartLabel.setText("Accuracy - last " + recent.size() + (recent.size() == 1 ? " session" : " sessions"));

        int maxHeight = dp(56);
        for (int i = recent.size() - 1; i >= 0; i--) {
            DatabaseHelper.SessionRecord s = recent.get(i);

            View bar = new View(this);
            int height = Math.max(dp(6), maxHeight * s.accuracy / 100);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(20), height);
            lp.setMargins(dp(4), 0, dp(4), 0);
            bar.setLayoutParams(lp);

            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(4));
            shape.setColor(i == 0 ? Color.parseColor("#75ff9e") : Color.parseColor("#3b4a3d"));
            bar.setBackground(shape);

            chartContainer.addView(bar);
        }
    }

    // Latest 5 sessions as rows: "Today - Hard Mode ........ 1,240 pts"
    private void buildRecentList(List<DatabaseHelper.SessionRecord> recent) {
        recentContainer.removeAllViews();
        tvNoSessions.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);
        btnPlayNow.setVisibility(recent.isEmpty() ? View.VISIBLE : View.GONE);

        int count = Math.min(5, recent.size());
        for (int i = 0; i < count; i++) {
            DatabaseHelper.SessionRecord s = recent.get(i);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = dp(8);
            row.setLayoutParams(rowLp);

            TextView left = new TextView(this);
            left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            left.setText(friendlyDate(s.playedAt) + " - " + capitalize(s.difficulty) + " Mode");
            left.setTextColor(Color.parseColor("#bacbb9"));
            left.setTextSize(14);

            TextView right = new TextView(this);
            right.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            right.setText(String.format(Locale.US, "%,d pts", s.score));
            right.setTextColor(Color.parseColor(i == 0 ? "#75ff9e" : "#e5e2e1"));
            right.setTextSize(14);
            right.setTypeface(right.getTypeface(), android.graphics.Typeface.BOLD);

            row.addView(left);
            row.addView(right);
            recentContainer.addView(row);
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.substring(0, 1).toUpperCase(Locale.US) + text.substring(1).toLowerCase(Locale.US);
    }

    private String friendlyDate(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        startOfDay(then);

        Calendar now = Calendar.getInstance();
        startOfDay(now);

        long days = Math.round((now.getTimeInMillis() - then.getTimeInMillis()) / 86400000.0);
        if (days <= 0) return "Today";
        if (days == 1) return "Yesterday";
        if (days < 7) return days + " days ago";
        return new SimpleDateFormat("d MMM", Locale.US).format(then.getTime());
    }

    private void startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }
}
