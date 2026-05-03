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
import com.midnight.music.utils.ThemeManager;

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

        binding.btnExportAll.setOnClickListener(v -> exportAllSongs());

        // Observe accent colour and tint buttons
        ThemeManager.getInstance(requireContext())
                .getAccentColor().observe(getViewLifecycleOwner(), color -> {
                    if (binding != null) {
                        if (binding.browseButton != null) {
                            binding.browseButton.setBackgroundTintList(
                                    android.content.res.ColorStateList.valueOf(color));
                        }
                        if (binding.btnExportAll != null) {
                            binding.btnExportAll.setTextColor(color);
                            binding.btnExportAll.setIconTint(android.content.res.ColorStateList.valueOf(color));
                        }
                    }
                });
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

    @Override
    public void onExportClick(Song song, int position) {
        if (song.getLocalPath() == null) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show() ;
            return;
        }

        exportSong(song);
    }

    private void exportAllSongs() {
        if (downloadedSongs == null || downloadedSongs.isEmpty()) return;

        Toast.makeText(requireContext(), "Exporting all songs...", Toast.LENGTH_SHORT).show();
        
        new Thread(() -> {
            int successCount = 0;
            for (Song song : downloadedSongs) {
                if (performExport(song)) {
                    successCount++;
                }
            }
            
            final int total = successCount;
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Exported " + total + " songs to Downloads", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private void exportSong(Song song) {
        Toast.makeText(requireContext(), "Exporting \"" + song.getSong() + "\"...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            boolean success = performExport(song);
            if (isAdded()) {
                requireActivity().runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(requireContext(), "Exported to Downloads", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "Export failed", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private boolean performExport(Song song) {
        if (song.getLocalPath() == null) return false;
        java.io.File sourceFile = new java.io.File(song.getLocalPath());
        if (!sourceFile.exists()) return false;

        String fileName = song.getSong().replaceAll("[\\\\/:*?\"<>|]", "_") + ".m4a";
        android.content.ContentValues values = new android.content.ContentValues();
        values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "audio/mp4");
        
        android.net.Uri collection;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
            values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS);
        } else {
            collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        }

        try {
            android.net.Uri uri = requireContext().getContentResolver().insert(collection, values);
            if (uri == null) return false;

            try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
                 java.io.OutputStream out = requireContext().getContentResolver().openOutputStream(uri)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return true;
            } catch (java.io.IOException e) {
                requireContext().getContentResolver().delete(uri, null, null);
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void showEmptyState(boolean show) {
        if (binding.headerActions != null) {
            binding.headerActions.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        binding.downloadsRecyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        binding.emptyStateContainer.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
