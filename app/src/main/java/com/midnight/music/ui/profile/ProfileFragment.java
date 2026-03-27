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
import android.content.Intent;

import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.auth.SessionManager;
import com.midnight.music.data.auth.AuthRepository;
import com.midnight.music.databinding.FragmentProfileBinding;
import com.midnight.music.ui.auth.AuthActivity;
import com.midnight.music.data.db.AppDatabase;

import java.util.concurrent.Executors;

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

        if (isLoggedIn) {
            binding.layoutLoggedIn.getRoot().setVisibility(View.VISIBLE);
            binding.layoutLoggedOut.getRoot().setVisibility(View.GONE);

            String nickname = sessionManager.getNickname();
            String email = sessionManager.getEmail();
            String avatarUrl = sessionManager.getAvatarUrl();

            binding.layoutLoggedIn.profileNickname.setText(nickname != null ? nickname : "User");
            binding.layoutLoggedIn.profileEmail.setText(email != null ? email : "");

            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                Glide.with(this)
                        .load(avatarUrl)
                        .placeholder(R.drawable.placeholder_profile)
                        .error(R.drawable.placeholder_profile)
                        .into(binding.layoutLoggedIn.profileAvatar);
            } else {
                binding.layoutLoggedIn.profileAvatar.setImageResource(R.drawable.placeholder_profile);
            }

            // Fetch stats quietly in the background
            fetchUserStats();

        } else {
            binding.layoutLoggedIn.getRoot().setVisibility(View.GONE);
            binding.layoutLoggedOut.getRoot().setVisibility(View.VISIBLE);
        }
    }

    private void fetchUserStats() {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(requireContext());
                int playlistCount = db.playlistDao().getAllPlaylistsSync().size();
                int likedSongsCount = db.songDao().getLikedSongsSync().size();

                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (binding != null && binding.layoutLoggedIn != null) {
                            binding.layoutLoggedIn.statPlaylistsCount.setText(String.valueOf(playlistCount));
                            binding.layoutLoggedIn.statLikedSongsCount.setText(String.valueOf(likedSongsCount));
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void setupClickListeners() {
        // --- LOGGED OUT STATE ---
        binding.layoutLoggedOut.btnLogin.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), AuthActivity.class));
        });

        binding.layoutLoggedOut.btnGuestAbout.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "About DayNight Music", Toast.LENGTH_SHORT).show();
        });

        binding.layoutLoggedOut.btnGuestSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.midnight.music.ui.settings.SettingsActivity.class));
        });

        // --- LOGGED IN STATE ---
        binding.layoutLoggedIn.llAccountDetails.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.midnight.music.ui.profile.EditProfileActivity.class));
        });

        binding.layoutLoggedIn.llSettings.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), com.midnight.music.ui.settings.SettingsActivity.class));
        });

        binding.layoutLoggedIn.llLogout.setOnClickListener(v -> {
            String accessToken = sessionManager.getAccessToken();
            if (accessToken != null) {
                // Background execution for network call
                Executors.newSingleThreadExecutor().execute(() -> {
                    new AuthRepository().signOut(accessToken);
                });
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
