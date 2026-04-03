package com.midnight.music.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.midnight.music.MainActivity;
import com.midnight.music.R;
import com.midnight.music.data.auth.AuthRepository;
import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.network.SupabaseApiClient;
import com.midnight.music.data.repository.CloudSyncManager;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Authentication screen supporting Email/Password and Google Sign-In.
 * Shows a nickname dialog for first-time registrations.
 */
public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";

    // UI Elements
    private MaterialButtonToggleGroup authToggleGroup;
    private TextInputLayout nicknameLayout, emailLayout, passwordLayout;
    private TextInputEditText etNickname, etEmail, etPassword;
    private MaterialButton btnSubmit, btnGoogleSignIn;
    private ProgressBar progressBar;
    private TextView tvError, tvSkip;

    // State
    private boolean isRegisterMode = false;

    // Services
    private AuthRepository authRepository;
    private SessionManager sessionManager;
    private GoogleSignInClient googleSignInClient;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleGoogleSignInResult(task);
                } else {
                    showError("Google Sign-In cancelled");
                    setLoading(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = SessionManager.getInstance(this);

        // Handle Deep Link for Email Confirmation if present
        Intent intent = getIntent();
        if (intent != null && intent.getAction() != null && intent.getAction().equals(Intent.ACTION_VIEW)) {
            android.net.Uri data = intent.getData();
            if (data != null && data.getScheme() != null && data.getScheme().equals("daynight") && data.getHost() != null && data.getHost().equals("auth")) {
                // Supabase sends tokens in the URL fragment (hash) for implicit flow: #access_token=...&refresh_token=...
                String fragment = data.getFragment();
                if (fragment != null && fragment.contains("access_token=")) {
                    try {
                        // Parse the fragment parameters
                        String[] params = fragment.split("&");
                        String accessToken = null;
                        String refreshToken = null;
                        
                        for (String param : params) {
                            if (param.startsWith("access_token=")) {
                                accessToken = param.substring("access_token=".length());
                            } else if (param.startsWith("refresh_token=")) {
                                refreshToken = param.substring("refresh_token=".length());
                            }
                        }

                        if (accessToken != null) {
                            // The user is now officially verified and logged in.
                            // However, we don't have their email/nickname in the URL alone.
                            // For simplicity, we save the tokens and let the sync pull data later.
                            sessionManager.saveSession(accessToken, refreshToken, null, null, null, null);
                            SupabaseApiClient.getInstance().setAccessToken(accessToken);
                            
                            Toast.makeText(this, "Email verified successfully!", Toast.LENGTH_LONG).show();
                            triggerSyncAndGo(); // Close auth screen
                            return;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse deep link tokens", e);
                    }
                }
                
                // If we get here, check for errors or PKCE code in query params
                String errorDesc = data.getQueryParameter("error_description");
                String errorCode = data.getQueryParameter("error_code");
                
                if ("otp_expired".equals(errorCode)) {
                    // This happens when an email scanner pre-consumes the link, verifying it in the background
                    Toast.makeText(this, "Account confirmed! Please log in.", Toast.LENGTH_LONG).show();
                } else if (errorDesc != null) {
                    Toast.makeText(this, "Link Error: " + errorDesc.replace("+", " "), Toast.LENGTH_LONG).show();
                } else {
                    // Show exactly what Supabase sent back so we can debug it
                    Toast.makeText(this, "Debug Link: " + data.toString(), Toast.LENGTH_LONG).show();
                }
            }
        }

        // If already logged in, there's no reason to be here, just finish
        if (sessionManager.isLoggedIn()) {
            finish();
            return;
        }

        setContentView(R.layout.activity_auth);

        authRepository = new AuthRepository();
        initGoogleSignIn();
        bindViews();
        setupListeners();
    }

    private void initGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.google_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void bindViews() {
        authToggleGroup = findViewById(R.id.authToggleGroup);
        nicknameLayout = findViewById(R.id.nicknameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        etNickname = findViewById(R.id.etNickname);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnSubmit = findViewById(R.id.btnSubmit);
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn);
        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);
        tvSkip = findViewById(R.id.tvSkip);
    }

    private void setupListeners() {
        // Toggle between Login / Register
        authToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                isRegisterMode = (checkedId == R.id.btnToggleRegister);
                nicknameLayout.setVisibility(isRegisterMode ? View.VISIBLE : View.GONE);
                btnSubmit.setText(isRegisterMode ? "Create Account" : "Login");
                tvError.setVisibility(View.GONE);
            }
        });

        // Email/Password submit
        btnSubmit.setOnClickListener(v -> {
            String email = getText(etEmail);
            String password = getText(etPassword);

            if (TextUtils.isEmpty(email)) {
                emailLayout.setError("Email is required");
                return;
            }
            if (TextUtils.isEmpty(password) || password.length() < 6) {
                passwordLayout.setError("Min 6 characters");
                return;
            }

            emailLayout.setError(null);
            passwordLayout.setError(null);
            tvError.setVisibility(View.GONE);
            setLoading(true);

            if (isRegisterMode) {
                String nickname = getText(etNickname);
                authRepository.signUp(email, password, nickname, authCallback);
            } else {
                authRepository.signIn(email, password, authCallback);
            }
        });

        // Google Sign-In
        btnGoogleSignIn.setOnClickListener(v -> {
            setLoading(true);
            tvError.setVisibility(View.GONE);
            // Sign out first to force account picker
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        });

        // Skip
        tvSkip.setOnClickListener(v -> finish());
    }

    private final AuthRepository.AuthCallback authCallback = new AuthRepository.AuthCallback() {
        @Override
        public void onSuccess(AuthRepository.AuthResult result) {
            runOnUiThread(() -> {
                setLoading(false);

                // Save session
                sessionManager.saveSession(
                        result.accessToken, result.refreshToken,
                        result.userId, result.email,
                        result.nickname, result.avatarUrl);

                SupabaseApiClient.getInstance().setAccessToken(result.accessToken);

                // If new user and no nickname, prompt for one
                if (result.isNewUser && TextUtils.isEmpty(result.nickname)) {
                    showNicknameDialog(result.accessToken);
                } else {
                    triggerSyncAndGo();
                }
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                setLoading(false);
                showError(message);
            });
        }
    };

    private void handleGoogleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            if (account != null && account.getIdToken() != null) {
                authRepository.signInWithGoogle(account.getIdToken(), authCallback);
            } else {
                showError("Failed to get Google ID token");
                setLoading(false);
            }
        } catch (ApiException e) {
            Log.e(TAG, "Google sign-in failed: " + e.getStatusCode(), e);
            showError("Google Sign-In failed (code " + e.getStatusCode() + ")");
            setLoading(false);
        }
    }

    /**
     * Show nickname dialog for first-time registrations.
     */
    private void showNicknameDialog(String accessToken) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nickname, null);
        TextInputEditText etNickDialog = dialogView.findViewById(R.id.etNicknameDialog);

        new AlertDialog.Builder(this, R.style.Theme_MidnightMusic_Dialog)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton("Save", (dialog, which) -> {
                    String nickname = etNickDialog.getText() != null
                            ? etNickDialog.getText().toString().trim() : "";
                    if (!nickname.isEmpty()) {
                        sessionManager.updateNickname(nickname);
                        // Update Supabase metadata
                        authRepository.updateNickname(accessToken, nickname, new AuthRepository.AuthCallback() {
                            @Override
                            public void onSuccess(AuthRepository.AuthResult result) {
                                // Also update profiles table
                                updateProfileNickname(nickname);
                            }
                            @Override
                            public void onError(String message) {
                                Log.e(TAG, "Failed to update nickname: " + message);
                            }
                        });
                    }
                    triggerSyncAndGo();
                })
                .setNegativeButton("Skip", (dialog, which) -> triggerSyncAndGo())
                .show();
    }

    private void updateProfileNickname(String nickname) {
        // Update in profiles table via PostgREST
        new Thread(() -> {
            try {
                com.google.gson.JsonObject body = new com.google.gson.JsonObject();
                body.addProperty("nickname", nickname);
                SupabaseApiClient.getInstance().getDataService()
                        .updateProfile(
                                "eq." + sessionManager.getUserId(),
                                body,
                                "return=minimal"
                        ).execute();
            } catch (Exception e) {
                Log.e(TAG, "Failed to update profile nickname", e);
            }
        }).start();
    }

    /**
     * Trigger a background sync, wait for completion, then close the auth screen.
     */
    private void triggerSyncAndGo() {
        setLoading(true);
        CloudSyncManager.getInstance(this).sync((success, message) -> {
            runOnUiThread(() -> {
                setLoading(false);
                if (!success) {
                    Log.e(TAG, "Sync failed after login: " + message);
                }
                finish();
            });
        });
    }

    // ============ UI Helpers ============

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSubmit.setEnabled(!loading);
        btnGoogleSignIn.setEnabled(!loading);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
