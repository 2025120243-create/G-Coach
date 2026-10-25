package com.nawawi.gcoach;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.HashSet;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchProcessor;

public class MainActivity extends BaseActivity {

    private static final int MIC_PERMISSION_CODE = 200;

    // UI Elements
    private TextView tvDetectedFrequency, tvDetectedNote, tvStatusText;
    private View tunerNeedle;
    private MaterialButton pegE2, pegA2, pegD3, pegG3, pegB3, pegE4;
    private MaterialButton currentSelectedButton = null;
    private Spinner tuningSpinner;

    // Tuning Logic Variables
    private float targetHz = 0f;
    private final float TUNING_TOLERANCE = 1.5f;
    private long successStartTime = 0;
    private final long REQUIRED_STABLE_TIME = 1500;
    private final HashSet<Integer> completedButtons = new HashSet<>();

    // [NEW] Keep the dispatcher here so we can stop the microphone later
    private AudioDispatcher dispatcher;

    // Frequencies and Labels that change based on Spinner selection
    private float freqE2, freqA2, freqD3, freqG3, freqB3, freqE4;
    private String noteE2, noteA2, noteD3, noteG3, noteB3, noteE4;

    // Theme Colors
    private final int COLOR_IDLE_STROKE = Color.parseColor("#859585");
    private final int COLOR_SELECTED_STROKE = Color.parseColor("#e5e2e1");
    private final int COLOR_GREEN = Color.parseColor("#75ff9e");
    private final int COLOR_FLAT = Color.parseColor("#ffb4ab");
    private final int COLOR_SHARP = Color.parseColor("#ffa8a3");

    // =====================================================================
    // Lifecycle
    // =====================================================================
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureLoggedIn()) return;

        setContentView(R.layout.activity_main);

        // Map Views
        tvDetectedFrequency = findViewById(R.id.tvDetectedFrequency);
        tvDetectedNote = findViewById(R.id.tvDetectedNote);
        tvStatusText = findViewById(R.id.tvStatusText);
        tunerNeedle = findViewById(R.id.tunerNeedle);
        tuningSpinner = findViewById(R.id.tuningSpinner);

        pegE2 = findViewById(R.id.pegE2);
        pegA2 = findViewById(R.id.pegA2);
        pegD3 = findViewById(R.id.pegD3);
        pegG3 = findViewById(R.id.pegG3);
        pegB3 = findViewById(R.id.pegB3);
        pegE4 = findViewById(R.id.pegE4);

        // Top bar: menu button on the left, profile avatar on the right
        setupTopBar("G-COACH", R.drawable.ic_menu, "Menu", v -> showMenuDialog(), true);
        setupBottomNav(TAB_TUNER);

        setupSpinner();
        attachPegListeners();

        // [CHANGED] Only ask for permission here. The microphone is started in onResume().
        if (!hasMicPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, MIC_PERMISSION_CODE);
        }
    }

    // [NEW] Start the microphone every time the screen comes to the front
    @Override
    protected void onResume() {
        super.onResume();
        if (hasMicPermission()) {
            startListening();
        }
    }

    // [NEW] Stop the microphone when the screen is hidden (so the game can use it)
    @Override
    protected void onPause() {
        super.onPause();
        stopListening();
    }

    // [NEW] Safety: make sure the microphone is released
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopListening();
    }

    // =====================================================================
    // Top-bar menu (the left button)
    // =====================================================================
    private void showMenuDialog() {
        String[] items = {"How to tune", "Reset strings", "My profile", "Log out"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Menu")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: showHowToTune(); break;
                        case 1: resetTuningState();
                                Toast.makeText(this, "Strings reset", Toast.LENGTH_SHORT).show();
                                break;
                        case 2: openProfile(); break;
                        case 3: confirmLogout(); break;
                    }
                })
                .show();
    }

    private void showHowToTune() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("How to tune")
                .setMessage("1. Pick a tuning at the top.\n"
                        + "2. Tap the string you want to tune.\n"
                        + "3. Pluck that string and let it ring.\n"
                        + "4. FLAT means tighten the peg, SHARP means loosen it.\n"
                        + "5. Hold it steady until the string turns green.")
                .setPositiveButton("Got it", null)
                .show();
    }

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
    // Tuning setup
    // =====================================================================
    private void setupSpinner() {
        String[] tuningOptions = {"Standard Tuning", "Drop D Tuning", "Half Step Down"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, tuningOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        tuningSpinner.setAdapter(adapter);

        tuningSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Change label colors for dark mode visibility
                if (parent.getChildAt(0) != null) {
                    ((TextView) parent.getChildAt(0)).setTextColor(Color.parseColor("#75ff9e"));
                }
                String selected = tuningOptions[position];
                setTuningFrequencies(selected);
                resetTuningState();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setTuningFrequencies(String tuningType) {
        switch (tuningType) {
            case "Drop D Tuning":
                freqE2 = 73.42f;  noteE2 = "D";  pegE2.setText("D");
                freqA2 = 110.00f; noteA2 = "A";  pegA2.setText("A");
                freqD3 = 146.83f; noteD3 = "D";  pegD3.setText("D");
                freqG3 = 196.00f; noteG3 = "G";  pegG3.setText("G");
                freqB3 = 246.94f; noteB3 = "B";  pegB3.setText("B");
                freqE4 = 329.63f; noteE4 = "e";  pegE4.setText("e");
                break;
            case "Half Step Down":
                freqE2 = 77.78f;  noteE2 = "Eb"; pegE2.setText("Eb");
                freqA2 = 103.83f; noteA2 = "Ab"; pegA2.setText("Ab");
                freqD3 = 138.59f; noteD3 = "Db"; pegD3.setText("Db");
                freqG3 = 185.00f; noteG3 = "Gb"; pegG3.setText("Gb");
                freqB3 = 233.08f; noteB3 = "Bb"; pegB3.setText("Bb");
                freqE4 = 311.13f; noteE4 = "eb"; pegE4.setText("eb");
                break;
            case "Standard Tuning":
            default:
                freqE2 = 82.41f;  noteE2 = "E";  pegE2.setText("E");
                freqA2 = 110.00f; noteA2 = "A";  pegA2.setText("A");
                freqD3 = 146.83f; noteD3 = "D";  pegD3.setText("D");
                freqG3 = 196.00f; noteG3 = "G";  pegG3.setText("G");
                freqB3 = 246.94f; noteB3 = "B";  pegB3.setText("B");
                freqE4 = 329.63f; noteE4 = "e";  pegE4.setText("e");
                break;
        }
    }

    private void attachPegListeners() {
        pegE2.setOnClickListener(v -> selectString(pegE2, freqE2, noteE2));
        pegA2.setOnClickListener(v -> selectString(pegA2, freqA2, noteA2));
        pegD3.setOnClickListener(v -> selectString(pegD3, freqD3, noteD3));
        pegG3.setOnClickListener(v -> selectString(pegG3, freqG3, noteG3));
        pegB3.setOnClickListener(v -> selectString(pegB3, freqB3, noteB3));
        pegE4.setOnClickListener(v -> selectString(pegE4, freqE4, noteE4));
    }

    private void resetTuningState() {
        targetHz = 0f;
        currentSelectedButton = null;
        completedButtons.clear();
        tvDetectedNote.setText("--");
        tvDetectedFrequency.setText("0.0 Hz");
        tvStatusText.setText("SELECT STRING");
        tvStatusText.setTextColor(COLOR_IDLE_STROKE);
        tunerNeedle.setTranslationX(0);
        tunerNeedle.setBackgroundColor(COLOR_IDLE_STROKE);

        MaterialButton[] buttons = {pegE2, pegA2, pegD3, pegG3, pegB3, pegE4};
        for (MaterialButton b : buttons) {
            b.setStrokeColor(ColorStateList.valueOf(COLOR_IDLE_STROKE));
        }
    }

    private void selectString(MaterialButton clickedButton, float frequency, String note) {
        targetHz = frequency;
        tvDetectedNote.setText(note);
        tvStatusText.setText("PLAY STRING");
        tvStatusText.setTextColor(COLOR_IDLE_STROKE);

        currentSelectedButton = clickedButton;
        successStartTime = 0;

        updateButtonVisuals();

        tunerNeedle.setTranslationX(0);
        tunerNeedle.setBackgroundColor(COLOR_IDLE_STROKE);
    }

    private void updateButtonVisuals() {
        MaterialButton[] buttons = {pegE2, pegA2, pegD3, pegG3, pegB3, pegE4};
        for (MaterialButton b : buttons) {
            if (completedButtons.contains(b.getId())) {
                b.setStrokeColor(ColorStateList.valueOf(COLOR_GREEN));
            } else if (b == currentSelectedButton) {
                b.setStrokeColor(ColorStateList.valueOf(COLOR_SELECTED_STROKE));
            } else {
                b.setStrokeColor(ColorStateList.valueOf(COLOR_IDLE_STROKE));
            }
        }
    }

    private void playSuccessSound() {
        try {
            MediaPlayer mp = MediaPlayer.create(this, R.raw.correct_sfx);
            if (mp == null) return; // [NEW] avoid crash if the sound cannot load
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.start();
        } catch (Exception e) {
            Log.e("G-Coach", "Sound play failed: " + e.getMessage());
        }
    }

    // =====================================================================
    // Microphone / pitch detection
    // =====================================================================
    private void startListening() {
        if (dispatcher != null) return; // [NEW] already listening, do not start twice

        try {
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(22050, 1024, 0);
        } catch (Exception e) {
            Log.e("G-Coach", "Could not open microphone: " + e.getMessage());
            dispatcher = null;
            return;
        }

        PitchDetectionHandler pitchHandler = (pitchDetectionResult, audioEvent) -> {
            final float pitchInHz = pitchDetectionResult.getPitch();

            runOnUiThread(() -> {
                if (targetHz > 0 && pitchInHz > 0 && currentSelectedButton != null) {
                    tvDetectedFrequency.setText(String.format("%.1f Hz", pitchInHz));

                    float diff = pitchInHz - targetHz;
                    float translationX = diff * 15f;
                    if (translationX > 300f) translationX = 300f;
                    if (translationX < -300f) translationX = -300f;
                    tunerNeedle.setTranslationX(translationX);

                    if (Math.abs(diff) <= TUNING_TOLERANCE) {
                        tvStatusText.setText("IN TUNE");
                        tvStatusText.setTextColor(COLOR_GREEN);
                        tunerNeedle.setBackgroundColor(COLOR_GREEN);

                        if (successStartTime == 0) {
                            successStartTime = System.currentTimeMillis();
                        } else if (System.currentTimeMillis() - successStartTime > REQUIRED_STABLE_TIME) {
                            if (!completedButtons.contains(currentSelectedButton.getId())) {
                                completedButtons.add(currentSelectedButton.getId());
                                updateButtonVisuals();
                                playSuccessSound();
                            }
                        }
                    } else {
                        successStartTime = 0;
                        if (diff < 0) {
                            tvStatusText.setText("FLAT");
                            tvStatusText.setTextColor(COLOR_FLAT);
                            tunerNeedle.setBackgroundColor(COLOR_FLAT);
                        } else {
                            tvStatusText.setText("SHARP");
                            tvStatusText.setTextColor(COLOR_SHARP);
                            tunerNeedle.setBackgroundColor(COLOR_SHARP);
                        }
                    }
                }
            });
        };

        AudioProcessor pitchProcessor = new PitchProcessor(
                PitchProcessor.PitchEstimationAlgorithm.YIN, 22050, 1024, pitchHandler);
        dispatcher.addAudioProcessor(pitchProcessor);
        new Thread(dispatcher, "Audio Thread").start();
    }

    // [NEW] Stops the microphone and frees it
    private void stopListening() {
        if (dispatcher != null) {
            dispatcher.stop();
            dispatcher = null;
        }
    }

    // =====================================================================
    // Permission
    // =====================================================================
    // [NEW] Only checks. It does not ask again (so onResume will not loop the pop-up)
    private boolean hasMicPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_PERMISSION_CODE && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        }
    }
}
