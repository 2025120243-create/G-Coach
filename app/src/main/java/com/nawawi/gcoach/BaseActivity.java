package com.nawawi.gcoach;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import java.util.Locale;

/**
 * Parent class for every screen that has the G-Coach top bar and/or bottom navigation.
 *
 * It does three jobs so no screen has to repeat them:
 *   1. makes sure someone is logged in
 *   2. fills in the top bar (title, optional left button, profile avatar that opens ProfileActivity)
 *   3. wires the bottom navigation and highlights the active tab
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected static final int TAB_NONE = -1;
    protected static final int TAB_HOME = 0;
    protected static final int TAB_TUNER = 1;
    protected static final int TAB_GAME = 2;
    protected static final int TAB_STATS = 3;

    private static final int COLOR_ACTIVE_ICON = Color.parseColor("#00612e");
    private static final int COLOR_IDLE_ICON = Color.parseColor("#bacbb9");
    private static final int COLOR_ACTIVE_LABEL = Color.parseColor("#e5e2e1");
    private static final int COLOR_IDLE_LABEL = Color.parseColor("#bacbb9");

    protected SessionManager session;

    private int activeTab = TAB_NONE;
    private TextView avatar;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        session = new SessionManager(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAvatar(); // the name may have been changed on the Profile screen
    }

    // =====================================================================
    // Login check
    // =====================================================================

    /**
     * Call right after super.onCreate(). If nobody is logged in this sends the user to the
     * login screen and returns false, so the caller should simply "return;".
     */
    protected boolean ensureLoggedIn() {
        if (session.isLoggedIn()) return true;
        goToLogin();
        return false;
    }

    protected void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // =====================================================================
    // Top bar
    // =====================================================================

    /** Standard bar: "G-COACH" title and the profile avatar on the right. */
    protected void setupTopBar() {
        setupTopBar("G-COACH", 0, null, null, true);
    }

    /**
     * @param title          text shown in the bar
     * @param leftIconRes    drawable for the left button (0 = no left button)
     * @param leftDescription accessibility text for the left button
     * @param leftAction     what the left button does
     * @param showAvatar     true = show the profile avatar on the right
     */
    protected void setupTopBar(String title,
                               @DrawableRes int leftIconRes,
                               @Nullable String leftDescription,
                               @Nullable View.OnClickListener leftAction,
                               boolean showAvatar) {
        TextView tvTitle = findViewById(R.id.tvTopTitle);
        ImageView left = findViewById(R.id.btnTopLeft);
        avatar = findViewById(R.id.btnProfile);

        tvTitle.setText(title);

        if (leftIconRes != 0 && leftAction != null) {
            left.setImageResource(leftIconRes);
            left.setContentDescription(leftDescription);
            left.setOnClickListener(leftAction);
            left.setVisibility(View.VISIBLE);
        } else {
            left.setVisibility(View.GONE);
        }

        if (showAvatar) {
            avatar.setVisibility(View.VISIBLE);
            avatar.setOnClickListener(v -> openProfile());
            refreshAvatar();
        } else {
            avatar.setVisibility(View.GONE);
        }
    }

    protected void refreshAvatar() {
        if (avatar != null && avatar.getVisibility() == View.VISIBLE && session != null) {
            avatar.setText(initialOf(session.getDisplayName()));
        }
    }

    protected static String initialOf(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) return "G";
        return trimmed.substring(0, 1).toUpperCase(Locale.getDefault());
    }

    protected void openProfile() {
        startActivity(new Intent(this, ProfileActivity.class));
    }

    // =====================================================================
    // Bottom navigation
    // =====================================================================

    protected void setupBottomNav(int active) {
        activeTab = active;
        bindTab(R.id.navHome, R.id.pillHome, R.id.iconHome, R.id.labelHome, TAB_HOME);
        bindTab(R.id.navTuner, R.id.pillTuner, R.id.iconTuner, R.id.labelTuner, TAB_TUNER);
        bindTab(R.id.navGame, R.id.pillGame, R.id.iconGame, R.id.labelGame, TAB_GAME);
        bindTab(R.id.navStats, R.id.pillStats, R.id.iconStats, R.id.labelStats, TAB_STATS);
    }

    private void bindTab(int itemId, int pillId, int iconId, int labelId, int tab) {
        View item = findViewById(itemId);
        View pill = findViewById(pillId);
        ImageView icon = findViewById(iconId);
        TextView label = findViewById(labelId);

        boolean isActive = tab == activeTab;
        pill.setBackgroundResource(isActive ? R.drawable.bg_nav_active : 0);
        ImageViewCompat.setImageTintList(icon,
                ColorStateList.valueOf(isActive ? COLOR_ACTIVE_ICON : COLOR_IDLE_ICON));
        label.setTextColor(isActive ? COLOR_ACTIVE_LABEL : COLOR_IDLE_LABEL);

        item.setOnClickListener(v -> goToTab(tab));
    }

    /**
     * Home is the root screen. The other tabs sit on top of it and replace each other, so the
     * Back button always returns to Home and never loops through old tabs.
     */
    protected void goToTab(int tab) {
        if (tab == activeTab) return; // already here

        Class<?> target;
        switch (tab) {
            case TAB_TUNER: target = MainActivity.class; break;
            case TAB_GAME:  target = DifficultyActivity.class; break;
            case TAB_STATS: target = ProgressActivity.class; break;
            case TAB_HOME:
            default:        target = DashboardActivity.class; break;
        }

        Intent intent = new Intent(this, target);
        if (tab == TAB_HOME) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        }
        startActivity(intent);
        overridePendingTransition(0, 0); // no slide animation between tabs

        // Leaving a non-Home tab: close it so tabs do not pile up behind each other
        if (activeTab != TAB_HOME) finish();
    }

    protected int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
