package com.example.midnightmusic.ui.premium;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.midnightmusic.databinding.FragmentPremiumBinding;
import com.google.android.material.button.MaterialButton;

public class PremiumFragment extends Fragment {
    private FragmentPremiumBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentPremiumBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupClickListeners();
    }

    private void setupClickListeners() {
        MaterialButton getPremiumButton = binding.getPremiumButton;
        getPremiumButton.setOnClickListener(v -> {
            // Handle premium subscription
            Toast.makeText(requireContext(),
                    "Redirecting to premium subscription...",
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 