package com.nawawi.gcoach;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;

public class GameActivity extends AppCompatActivity {

    // ---------- Game settings ----------
    private static final int MIC_PERMISSION_CODE = 201;
    private static final int SAMPLE_RATE = 22050;
    private static final int BUFFER_SIZE = 2048;          // bigger buffer = better for low E string
    private static final int TOTAL_ROUNDS = 10;
    private static final float MIN_PROBABILITY = 0.85f;   // ignore weak / noisy pitch readings
    private static final long HOLD_MS = 350;              // note must stay correct for this long
    private static final long DROPOUT_GRACE_MS = 250;     // small gaps in the sound are OK
    private static final long IGNORE_AFTER_START_MS = 500; // ignore the previous note still ringing
    private static final long NEXT_ROUND_DELAY_MS = 1200;

    // Standard tuning, string 6 (low E) to string 1 (high E)
    private static final float[] OPEN_FREQ = {82.41f, 110.00f, 146.83f, 196.00f, 246.94f, 329.63f};
    private static final String[] STRING_LABELS = {
            "6th string (low E)", "5th string (A)", "4th string (D)",
            "3rd string (G)", "2nd string (B)", "1st string (high E)"};
    private static final String[] NOTE_NAMES =
            {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

    // Colors (same theme as the rest of the app)
    private static final int COLOR_GREEN = Color.parseColor("#75ff9e");
    private static final int COLOR_IDLE = Color.parseColor("#bacbb9");
    private static final int COLOR_WARN = Color.parseColor("#ffb347");
    private static final int COLOR_BAD = Color.parseColor("#ffb4ab");
    private static final int CARD_NORMAL = Color.parseColor("#2a2a2a");
    private static final int CARD_HIT = Color.parseColor("#1b4d2e");
    private static final int CARD_MISS = Color.parseColor("#5c1f1f");

    private enum State { IDLE, LOCKED, PLAYING, FINISHED }

    private static class Challenge {
        final int stringIndex;
        final int fret;
        final float freq;
        final String noteName;

        Challenge(int stringIndex, int fret, float freq, String noteName) {
            this.stringIndex = stringIndex;
            this.fret = fret;
            this.freq = freq;
            this.noteName = noteName;
        }
    }

    // ---------- Views ----------
    private TextView targetNoteText, feedbackText, scoreText, streakText, roundText;
    private TextView timeText, difficultyText, hintText, detectedText;
    private ProgressBar timeProgress;
    private CardView gameCard;
    private ImageView btnBack;
    private MaterialButton btnSkip;

    // ---------- Difficulty ----------
    private String difficulty = "EASY";
    private int maxFret = 0;
    private long roundMs = 15000;
    private float toleranceCents = 40f;
    private double multiplier = 1.0;
    private final List<Challenge> pool = new ArrayList<>();

    // ---------- Game state ----------
    private State state = State.IDLE;
    private Challenge current = null;
    private Challenge previous = null;
    private int score = 0, round = 0, hits = 0, streak = 0, bestStreak = 0;
    private long remainingMs = 0;
    private long roundStartTime = 0, holdStart = 0, lastMatchTime = 0;

    // ---------- Helpers ----------
    private final Random random = new Random();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nextRoundRunnable = this::startRound;
    private CountDownTimer roundTimer;
    private AudioDispatcher dispatcher;
    private AlertDialog resultDialog;

    // =====================================================================
    // Lifecycle
    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mapViews();

        String level = getIntent().getStringExtra("DIFFICULTY");
        if (level != null) difficulty = level;
        configureDifficulty();
        buildPool();

        btnBack.setOnClickListener(v -> finish());
        btnSkip.setOnClickListener(v -> {
            if (state == State.PLAYING) onMiss("Skipped! It was " + current.noteName);
        });

        updateHud();
        if (hasMicPermission()) {
            beginGame();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Free the microphone and stop the clock when the user leaves the screen
        stopListening();
        if (roundTimer != null) roundTimer.cancel();
        handler.removeCallbacks(nextRoundRunnable);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (state == State.PLAYING) {
            startListening();
            startTimer(Math.max(remainingMs, 1000));
        } else if (state == State.LOCKED) {
            startListening();
            scheduleNextRound(600);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
        if (roundTimer != null) roundTimer.cancel();
        handler.removeCallbacksAndMessages(null);
        if (resultDialog != null && resultDialog.isShowing()) resultDialog.dismiss();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                beginGame();
            } else {
                Toast.makeText(this, "Microphone permission is needed to play the game.",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // =====================================================================
    // Setup
    // =====================================================================
    private void mapViews() {
        targetNoteText = findViewById(R.id.targetNoteText);
        feedbackText = findViewById(R.id.feedbackText);
        scoreText = findViewById(R.id.scoreText);
        streakText = findViewById(R.id.streakText);
        roundText = findViewById(R.id.roundText);
        timeText = findViewById(R.id.timeText);
        difficultyText = findViewById(R.id.difficultyText);
        hintText = findViewById(R.id.hintText);
        detectedText = findViewById(R.id.detectedText);
        timeProgress = findViewById(R.id.timeProgress);
        gameCard = findViewById(R.id.gameCard);
        btnBack = findViewById(R.id.btnBack);
        btnSkip = findViewById(R.id.btnSkip);
    }

    private void configureDifficulty() {
        switch (difficulty) {
            case "MEDIUM":
                maxFret = 5;
                roundMs = 12000;
                toleranceCents = 35f;
                multiplier = 1.5;
                break;
            case "HARD":
                maxFret = 12;
                roundMs = 9000;
                toleranceCents = 25f;
                multiplier = 2.0;
                break;
            case "EASY":
            default:
                difficulty = "EASY";
                maxFret = 0;
                roundMs = 15000;
                toleranceCents = 40f;
                multiplier = 1.0;
                break;
        }
        difficultyText.setText(difficulty);
    }

    // Easy = open strings, Medium = frets 0-5, Hard = frets 0-12
    private void buildPool() {
        pool.clear();
        for (int s = 0; s < OPEN_FREQ.length; s++) {
            for (int f = 0; f <= maxFret; f++) {
                float freq = (float) (OPEN_FREQ[s] * Math.pow(2.0, f / 12.0));
                pool.add(new Challenge(s, f, freq, noteNameFor(freq)));
            }
        }
    }

    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    // =====================================================================
    // Game flow
    // =====================================================================
    private void beginGame() {
        resetGame();
        startListening();
    }

    private void resetGame() {
        handler.removeCallbacks(nextRoundRunnable);
        if (roundTimer != null) roundTimer.cancel();

        score = 0;
        round = 0;
        hits = 0;
        streak = 0;
        bestStreak = 0;
        previous = null;
        current = null;
        state = State.LOCKED;

        targetNoteText.setText("?");
        hintText.setText("");
        detectedText.setText("");
        feedbackText.setText("Get ready...");
        feedbackText.setTextColor(COLOR_IDLE);
        gameCard.setCardBackgroundColor(CARD_NORMAL);
        timeProgress.setProgress(1000);
        timeText.setText("");
        updateHud();
        scheduleNextRound(800);
    }

    private void scheduleNextRound(long delayMs) {
        handler.removeCallbacks(nextRoundRunnable);
        handler.postDelayed(nextRoundRunnable, delayMs);
    }

    private void startRound() {
        if (round >= TOTAL_ROUNDS) {
            endGame();
            return;
        }
        round++;
        current = pickNext();
        previous = current;

        targetNoteText.setText(current.noteName);
        hintText.setText(hintFor(current));
        detectedText.setText("");
        feedbackText.setText("Play this note on your guitar");
        feedbackText.setTextColor(COLOR_IDLE);
        gameCard.setCardBackgroundColor(CARD_NORMAL);
        updateHud();

        holdStart = 0;
        lastMatchTime = 0;
        roundStartTime = System.currentTimeMillis();
        state = State.PLAYING;
        startTimer(roundMs);
    }

    private Challenge pickNext() {
        Challenge next;
        int guard = 0;
        do {
            next = pool.get(random.nextInt(pool.size()));
            guard++;
            // Do not repeat the same pitch twice in a row
        } while (previous != null && Math.abs(next.freq - previous.freq) < 1f && guard < 20);
        return next;
    }

    private String hintFor(Challenge c) {
        if (c.fret == 0) return "Open " + STRING_LABELS[c.stringIndex];
        return STRING_LABELS[c.stringIndex] + " \u00B7 fret " + c.fret;
    }

    private void startTimer(long durationMs) {
        if (roundTimer != null) roundTimer.cancel();
        remainingMs = durationMs;
        updateTimeUi();
        roundTimer = new CountDownTimer(durationMs, 100) {
            @Override
            public void onTick(long millisLeft) {
                remainingMs = millisLeft;
                updateTimeUi();
            }

            @Override
            public void onFinish() {
                remainingMs = 0;
                updateTimeUi();
                if (state == State.PLAYING) onMiss("Time's up! It was " + current.noteName);
            }
        }.start();
    }

    private void updateTimeUi() {
        timeProgress.setProgress((int) (remainingMs * 1000 / roundMs));
        long seconds = (remainingMs + 999) / 1000;
        timeText.setText(seconds + "s");
        timeText.setTextColor(seconds <= 3 ? COLOR_BAD : COLOR_IDLE);
    }

    // =====================================================================
    // Pitch detection
    // =====================================================================
    private void startListening() {
        if (dispatcher != null) return;
        try {
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SAMPLE_RATE, BUFFER_SIZE, 0);
        } catch (Exception e) {
            Log.e("G-Coach", "Could not open microphone: " + e.getMessage());
            Toast.makeText(this, "Could not open the microphone.", Toast.LENGTH_LONG).show();
            dispatcher = null;
            return;
        }

        PitchDetectionHandler pitchHandler = (result, audioEvent) -> {
            final float pitch = result.getPitch();
            final float probability = result.getProbability();
            runOnUiThread(() -> handlePitch(pitch, probability));
        };

        dispatcher.addAudioProcessor(new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN,
                SAMPLE_RATE, BUFFER_SIZE, pitchHandler));
        new Thread(dispatcher, "Game Audio Thread").start();
    }

    private void stopListening() {
        if (dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
        }
    }

    private void handlePitch(float pitch, float probability) {
        if (state != State.PLAYING || current == null) return;

        long now = System.currentTimeMillis();

        // No clear sound: allow a short gap before the "hold" is reset
        if (pitch <= 0 || probability < MIN_PROBABILITY) {
            if (now - lastMatchTime > DROPOUT_GRACE_MS) holdStart = 0;
            return;
        }
        // Ignore the first moment of the round (old note may still be ringing)
        if (now - roundStartTime < IGNORE_AFTER_START_MS) return;

        float cents = (float) (1200.0 * Math.log(pitch / current.freq) / Math.log(2.0));
        detectedText.setText(String.format(Locale.US, "Heard: %s (%.1f Hz)",
                noteNameFor(pitch), pitch));

        if (Math.abs(cents) <= toleranceCents) {
            lastMatchTime = now;
            if (holdStart == 0) holdStart = now;
            feedbackText.setText("Nice! Hold it...");
            feedbackText.setTextColor(COLOR_GREEN);
            if (now - holdStart >= HOLD_MS) onHit();
            return;
        }

        // Not matching right now
        if (now - lastMatchTime > DROPOUT_GRACE_MS) holdStart = 0;

        // Same note name but different octave?
        float octaves = Math.round(cents / 1200f);
        float residual = cents - octaves * 1200f;
        if (octaves != 0 && Math.abs(residual) <= toleranceCents) {
            feedbackText.setText("Right note, wrong octave. Try the spot shown.");
            feedbackText.setTextColor(COLOR_WARN);
        } else if (Math.abs(cents) <= 100) {
            feedbackText.setText(cents < 0
                    ? "Almost! A little flat \u2191"
                    : "Almost! A little sharp \u2193");
            feedbackText.setTextColor(COLOR_WARN);
        } else {
            feedbackText.setText("Not yet, try again");
            feedbackText.setTextColor(COLOR_IDLE);
        }
    }

    // =====================================================================
    // Hit / miss / end
    // =====================================================================
    private void onHit() {
        state = State.LOCKED;
        if (roundTimer != null) roundTimer.cancel();

        hits++;
        streak++;
        if (streak > bestStreak) bestStreak = streak;

        int timeBonus = (int) (remainingMs / 1000) * 10;
        int streakBonus = Math.min(streak, 5) * 10;
        int gained = (int) Math.round((100 + timeBonus + streakBonus) * multiplier);
        score += gained;

        gameCard.setCardBackgroundColor(CARD_HIT);
        feedbackText.setText("HIT! +" + gained);
        feedbackText.setTextColor(COLOR_GREEN);
        updateHud();
        playSuccessSound();
        scheduleNextRound(NEXT_ROUND_DELAY_MS);
    }

    private void onMiss(String message) {
        state = State.LOCKED;
        if (roundTimer != null) roundTimer.cancel();

        streak = 0;
        gameCard.setCardBackgroundColor(CARD_MISS);
        feedbackText.setText(message);
        feedbackText.setTextColor(COLOR_BAD);
        updateHud();
        scheduleNextRound(NEXT_ROUND_DELAY_MS);
    }

    private void endGame() {
        state = State.FINISHED;
        if (roundTimer != null) roundTimer.cancel();
        stopListening();

        int accuracy = Math.round(hits * 100f / TOTAL_ROUNDS);

        SharedPreferences prefs = getSharedPreferences("gcoach_prefs", MODE_PRIVATE);
        int oldBest = prefs.getInt("best_" + difficulty, 0);
        boolean newBest = score > oldBest;
        prefs.edit()
                .putInt("best_" + difficulty, Math.max(oldBest, score))
                .putInt("sessions_total", prefs.getInt("sessions_total", 0) + 1)
                .apply();

        String message = String.format(Locale.US,
                "Score: %d%s\nAccuracy: %d%% (%d of %d notes)\nBest streak: %d\nBest on %s: %d",
                score, newBest ? "  \u2605 NEW BEST!" : "",
                accuracy, hits, TOTAL_ROUNDS, bestStreak,
                difficulty, Math.max(oldBest, score));

        resultDialog = new AlertDialog.Builder(this)
                .setTitle("Round complete")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("Play again", (d, w) -> beginGame())
                .setNegativeButton("Back to menu", (d, w) -> finish())
                .show();
    }

    // =====================================================================
    // Small helpers
    // =====================================================================
    private void updateHud() {
        scoreText.setText(String.valueOf(score));
        streakText.setText(streak >= 2 ? "Streak x" + streak : "");
        roundText.setText(String.format(Locale.US, "NOTE %d / %d", Math.max(round, 0), TOTAL_ROUNDS));
    }

    private String noteNameFor(float freq) {
        int midi = Math.round((float) (69 + 12 * (Math.log(freq / 440.0) / Math.log(2.0))));
        return NOTE_NAMES[((midi % 12) + 12) % 12];
    }

    private void playSuccessSound() {
        try {
            MediaPlayer mp = MediaPlayer.create(this, R.raw.correct_sfx);
            if (mp == null) return;
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } catch (Exception e) {
            Log.e("G-Coach", "Sound play failed: " + e.getMessage());
        }
    }
}
