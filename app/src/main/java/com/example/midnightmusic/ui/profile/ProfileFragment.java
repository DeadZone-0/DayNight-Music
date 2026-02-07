package com.example.midnightmusic.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.midnightmusic.databinding.FragmentProfileBinding;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ProfileFragment extends Fragment {
    private FragmentProfileBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
    }

    private void setupClickListeners() {
        // Dark mode switch
        SwitchMaterial darkModeSwitch = binding.darkModeSwitch;
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // TODO: Implement dark mode toggle
            String message = isChecked ? "Dark mode enabled" : "Dark mode disabled";
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });

        // About button
        binding.aboutButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "About Midnight Music",
                    Toast.LENGTH_SHORT).show();
        });

        // Logout button
        binding.logoutButton.setOnClickListener(v -> {
            Toast.makeText(requireContext(),
                    "Logging out...",
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 