package com.midnight.music.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.midnight.music.R;
import com.midnight.music.data.model.Song;
import com.midnight.music.data.repository.MusicRepository;
import com.midnight.music.databinding.FragmentDownloadsBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.adapters.DownloadedSongsAdapter;

import java.util.ArrayList;
import java.util.List;

public class DownloadsFragment extends Fragment implements DownloadedSongsAdapter.OnItemClickListener {
    private FragmentDownloadsBinding binding;
    private DownloadedSongsAdapter adapter;
    private MusicRepository repository;
    private List<Song> downloadedSongs = new ArrayList<>();

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
        repository = MusicRepository.getInstance(requireContext());
        setupRecyclerView();
        setupEmptyState();
        observeDownloads();
    }

    private void setupRecyclerView() {
        adapter = new DownloadedSongsAdapter(this);
        binding.downloadsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.downloadsRecyclerView.setAdapter(adapter);
    }

    private void setupEmptyState() {
        binding.browseButton.setOnClickListener(v -> {
            // Navigate to search fragment
            Navigation.findNavController(requireView())
                    .navigate(R.id.navigation_search);
        });
    }

    private void observeDownloads() {
        repository.getDownloadedSongs().observe(getViewLifecycleOwner(), songs -> {
            downloadedSongs = songs != null ? songs : new ArrayList<>();
            adapter.submitList(new ArrayList<>(downloadedSongs));
            showEmptyState(downloadedSongs.isEmpty());
        });
    }

    @Override
    public void onSongClick(Song song, int position) {
        // Play the downloaded song (and set all downloads as queue)
        MusicPlayerManager playerManager = MusicPlayerManager.getInstance(requireContext());
        playerManager.playQueue(downloadedSongs, position);
    }

    @Override
    public void onDeleteClick(Song song, int position) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Download")
                .setMessage("Remove \"" + song.getSong() + "\" from downloads?")
                .setPositiveButton("Remove", (dialog, which) -> {
                    repository.deleteDownload(requireContext(), song, () -> {
                        requireActivity().runOnUiThread(() -> {
                            Toast.makeText(requireContext(), "Download removed", Toast.LENGTH_SHORT).show();
                        });
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
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
