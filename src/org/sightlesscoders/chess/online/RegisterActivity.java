package org.sightlesscoders.chess.online;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sightlesscoders.chess.MainActivity;

public class RegisterActivity extends Activity {
    private AuthRepository authRepository;
    private EditText emailEdit;
    private EditText passwordEdit;
    private EditText confirmEdit;
    private Button registerButton;
    private Button backButton;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authRepository = new AuthRepository(this);
        buildUi();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 48, 48, 48);
        root.setBackgroundColor(0xFF121212);

        TextView title = new TextView(this);
        title.setText("Create Account");
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
        passwordEdit.setHint("Password (min 6 chars)");
        passwordEdit.setTextColor(0xFFFFFFFF);
        passwordEdit.setHintTextColor(0xFF888888);
        passwordEdit.setBackgroundResource(android.R.drawable.edit_text);
        passwordEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(passwordEdit, wrapParams());

        confirmEdit = new EditText(this);
        confirmEdit.setHint("Confirm Password");
        confirmEdit.setTextColor(0xFFFFFFFF);
        confirmEdit.setHintTextColor(0xFF888888);
        confirmEdit.setBackgroundResource(android.R.drawable.edit_text);
        confirmEdit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(confirmEdit, wrapParams());

        statusView = new TextView(this);
        statusView.setTextColor(0xFFFF4444);
        statusView.setTextSize(14f);
        statusView.setGravity(android.view.Gravity.CENTER);
        statusView.setPadding(0, 16, 0, 16);
        statusView.setVisibility(View.GONE);
        root.addView(statusView, wrapParams());

        registerButton = new Button(this);
        registerButton.setText("Create Account");
        registerButton.setOnClickListener(v -> attemptRegister());
        root.addView(registerButton, wrapParams());

        backButton = new Button(this);
        backButton.setText("Back to Login");
        backButton.setOnClickListener(v -> finish());
        root.addView(backButton, wrapParams());

        setContentView(root);
    }

    private void attemptRegister() {
        String email = emailEdit.getText().toString().trim();
        String password = passwordEdit.getText().toString();
        String confirm = confirmEdit.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Please fill all fields");
            return;
        }

        if (!password.equals(confirm)) {
            showError("Passwords don't match");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters");
            return;
        }

        setLoading(true);
        authRepository.signUp(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(String idToken, String userId, String email) {
                runOnUiThread(() -> {
                    setLoading(false);
                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    setLoading(false);
                    showError("Registration failed: " + e.getMessage());
                });
            }
        });
    }

    private void showError(String message) {
        statusView.setText(message);
        statusView.setVisibility(View.VISIBLE);
    }

    private void setLoading(boolean loading) {
        registerButton.setEnabled(!loading);
        backButton.setEnabled(!loading);
        emailEdit.setEnabled(!loading);
        passwordEdit.setEnabled(!loading);
        confirmEdit.setEnabled(!loading);
        if (loading) {
            registerButton.setText("Creating...");
        } else {
            registerButton.setText("Create Account");
        }
    }

    private LinearLayout.LayoutParams wrapParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}