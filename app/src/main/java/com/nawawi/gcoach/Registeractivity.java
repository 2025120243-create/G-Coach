package com.nawawi.gcoach;

import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class RegisterActivity extends AppCompatActivity {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private TextInputLayout tilName, tilUsername, tilPassword, tilConfirm;
    private TextInputEditText etName, etUsername, etPassword, etConfirm;
    private DatabaseHelper db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        db = DatabaseHelper.getInstance(this);

        tilName = findViewById(R.id.tilName);
        tilUsername = findViewById(R.id.tilUsername);
        tilPassword = findViewById(R.id.tilPassword);
        tilConfirm = findViewById(R.id.tilConfirm);
        etName = findViewById(R.id.etName);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        etConfirm = findViewById(R.id.etConfirm);
        MaterialButton btnRegister = findViewById(R.id.btnRegister);
        TextView tvGoLogin = findViewById(R.id.tvGoLogin);

        btnRegister.setOnClickListener(v -> attemptRegister());

        etConfirm.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister();
                return true;
            }
            return false;
        });

        tvGoLogin.setOnClickListener(v -> finish());
    }

    private String text(TextInputEditText et) {
        return et.getText() == null ? "" : et.getText().toString();
    }

    private void attemptRegister() {
        tilName.setError(null);
        tilUsername.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        String name = text(etName).trim();
        String username = text(etUsername).trim();
        String password = text(etPassword);
        String confirm = text(etConfirm);

        if (name.length() < 2) {
            tilName.setError("Enter your name (at least 2 characters)");
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            tilUsername.setError("3-20 characters: letters, numbers or underscore only");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            tilPassword.setError("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
            return;
        }
        if (!password.equals(confirm)) {
            tilConfirm.setError("Passwords do not match");
            return;
        }

        long userId = db.registerUser(username, name, password);
        if (userId == -1) {
            tilUsername.setError("That username is already taken");
            return;
        }

        // Log the new user in straight away
        SessionManager session = new SessionManager(this);
        session.login(new DatabaseHelper.User(userId, username, name));

        Toast.makeText(this, "Account created. Welcome, " + name + "!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(this, DashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}