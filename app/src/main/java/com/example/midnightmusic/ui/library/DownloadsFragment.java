package com.example.midnightmusic.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.databinding.FragmentDownloadsBinding;

public class DownloadsFragment extends Fragment {
    private FragmentDownloadsBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentDownloadsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupRecyclerView();
        setupEmptyState();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = binding.downloadsRecyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        // TODO: Set adapter when implementing downloads functionality
        
        // For now, show empty state
        showEmptyState(true);
    }

    private void setupEmptyState() {
        binding.browseButton.setOnClickListener(v -> {
            // Navigate to search fragment
            Navigation.findNavController(requireView())
                    .navigate(R.id.navigation_search);
        });
    }

    private void showEmptyState(boolean show) {
        binding.downloadsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.emptyStateContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 