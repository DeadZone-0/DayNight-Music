package com.midnight.music.ui.profile;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.auth.AuthRepository;
import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.databinding.FragmentEditProfileBinding;

public class EditProfileActivity extends AppCompatActivity {
    private FragmentEditProfileBinding binding;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = FragmentEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        sessionManager = SessionManager.getInstance(this);

        setupUI();
        setupClickListeners();
    }

    private void setupUI() {
        String nickname = sessionManager.getNickname();
        String email = sessionManager.getEmail();
        String avatarUrl = sessionManager.getAvatarUrl();

        if (nickname != null) {
            binding.etNickname.setText(nickname);
            binding.etNickname.setSelection(nickname.length());
        }
        
        if (email != null) {
            binding.etEmail.setText(email);
        }

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.placeholder_profile)
                    .error(R.drawable.placeholder_profile)
                    .into(binding.ivAvatar);
        }
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.fabEditAvatar.setOnClickListener(v -> {
            Toast.makeText(this, "Profile picture upload coming soon!", Toast.LENGTH_SHORT).show();
        });

        binding.btnSaveChanges.setOnClickListener(v -> {
            String newNickname = binding.etNickname.getText().toString().trim();
            String currentNickname = sessionManager.getNickname();

            if (newNickname.isEmpty()) {
                Toast.makeText(this, "Nickname cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }

            if (newNickname.equals(currentNickname)) {
                finish();
                return;
            }

            saveChanges(newNickname);
        });
    }

    private void saveChanges(String newNickname) {
        String accessToken = sessionManager.getAccessToken();
        if (accessToken == null) return;

        binding.btnSaveChanges.setEnabled(false);
        binding.btnSaveChanges.setText("Saving...");

        new AuthRepository().updateNickname(accessToken, newNickname, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(AuthRepository.AuthResult result) {
                runOnUiThread(() -> {
                    sessionManager.updateNickname(newNickname);
                    Toast.makeText(EditProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    binding.btnSaveChanges.setEnabled(true);
                    binding.btnSaveChanges.setText("Save Changes");
                    Toast.makeText(EditProfileActivity.this, "Update failed: " + message, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
