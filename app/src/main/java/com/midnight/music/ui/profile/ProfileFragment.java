package com.midnight.music.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.auth.AuthRepository;
import com.midnight.music.databinding.FragmentProfileBinding;
import com.midnight.music.ui.auth.AuthActivity;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;
    private SessionManager sessionManager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        sessionManager = SessionManager.getInstance(requireContext());
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
    }

    @Override
    public void onResume() {
        super.onResume();
        updateUI();
    }

    private void updateUI() {
        boolean isLoggedIn = sessionManager.isLoggedIn();

        // Control visibility
        binding.profileImage.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        binding.profileName.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        binding.profileEmail.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);
        binding.logoutButton.setVisibility(isLoggedIn ? View.VISIBLE : View.GONE);

        binding.loginButton.setVisibility(isLoggedIn ? View.GONE : View.VISIBLE);
        binding.loginDivider.setVisibility(isLoggedIn ? View.GONE : View.VISIBLE);

        if (isLoggedIn) {
            String nickname = sessionManager.getNickname();
            String email = sessionManager.getEmail();
            String avatarUrl = sessionManager.getAvatarUrl();

            binding.profileName.setText(nickname != null ? nickname : "User");
            binding.profileEmail.setText(email != null ? email : "");

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.placeholder_profile)
                        .error(R.drawable.placeholder_profile)
                        .into(binding.profileImage);
            } else {
                binding.profileImage.setImageResource(R.drawable.placeholder_profile);
            }
        }
    }

    private void setupClickListeners() {
        // Login button
        binding.loginButton.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AuthActivity.class));
        });

        // About button
        binding.aboutButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "About DayNight Music",
                    Toast.LENGTH_SHORT).show();
        });

        // Logout button
        binding.logoutButton.setOnClickListener(v -> {
            String accessToken = sessionManager.getAccessToken();
            if (accessToken != null) {
                new AuthRepository().signOut(accessToken);
            }
            sessionManager.clearSession();
            updateUI();
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 
