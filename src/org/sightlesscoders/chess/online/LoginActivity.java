package org.sightlesscoders.chess.online;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sightlesscoders.chess.MainActivity;

public class LoginActivity extends Activity {
    private AuthRepository authRepository;
    private EditText emailEdit;
    private EditText passwordEdit;
    private Button loginButton;
    private Button registerButton;
    private Button guestButton;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);

        if (authRepository.isLoggedIn()) {
            startMainActivity();
            return;
        }

        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(0xFF121212);

        TextView title = new TextView(this);
        title.setText("Chess - Sign In");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28f);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, 0, 0, 48);
        root.addView(title);

        emailEdit = new EditText(this);
        emailEdit.setHint("Email");
        emailEdit.setTextColor(0xFFFFFFFF);
        emailEdit.setHintTextColor(0xFF888888);
        emailEdit.setBackgroundResource(android.R.drawable.edit_text);
        emailEdit.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        root.addView(emailEdit, wrapParams());

        passwordEdit = new EditText(this);
        passwordEdit.setHint("Password");
        passwordEdit.setTextColor(0xFFFFFFFF);
        passwordEdit.setHintTextColor(0xFF888888);
        passwordEdit.setBackgroundResource(android.R.drawable.edit_text);
        passwordEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordEdit, wrapParams());

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFF4444);
        statusView.setTextSize(14f);
        statusView.setGravity(android.view.Gravity.CENTER);
        statusView.setPadding(0, 16, 0, 16);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, wrapParams());

        loginButton = new Button(this);
        loginButton.setText("Sign In");
        loginButton.setOnClickListener(v -> attemptLogin());
        root.addView(loginButton, wrapParams());

        registerButton = new Button(this);
        registerButton.setText("Create Account");
        registerButton.setOnClickListener(v -> startRegisterActivity());
        root.addView(registerButton, wrapParams());

        guestButton = new Button(this);
        guestButton.setText("Play as Guest");
        guestButton.setOnClickListener(v -> signInAnonymously());
        root.addView(guestButton, wrapParams());

        setContentView(root);
    }

    private void attemptLogin() {
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Please enter email and password");
            return;
        }

        setLoading(true);
        authRepository.signIn(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String idToken, String userId, String email) {
                runOnUiThread(() -> {
                    setLoading(false);
                    startMainActivity();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("Login failed: " + e.getMessage());
                });
            }
        });
    }

    private void signInAnonymously() {
        setLoading(true);
        authRepository.signInAnonymously(new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String idToken, String userId, String email) {
                runOnUiThread(() -> {
                    setLoading(false);
                    startMainActivity();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("Guest sign-in failed: " + e.getMessage());
                });
            }
        });
    }

    private void startRegisterActivity() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void startMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void showError(String message) {
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
        guestButton.setEnabled(!loading);
        emailEdit.setEnabled(!loading);
        passwordEdit.setEnabled(!loading);
        if (loading) {
            loginButton.setText("Signing in...");
        } else {
            loginButton.setText("Sign In");
        }
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}