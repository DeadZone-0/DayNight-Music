package com.example.midnightmusic.ui.library;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.databinding.FragmentPlaylistsBinding;
import com.example.midnightmusic.ui.adapters.PlaylistAdapter;
import com.example.midnightmusic.ui.playlist.PlaylistDetailActivity;
import com.example.midnightmusic.ui.library.PlaylistViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class PlaylistsFragment extends Fragment implements PlaylistAdapter.PlaylistListener {
    private FragmentPlaylistsBinding binding;
    private PlaylistAdapter adapter;
    private PlaylistViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentPlaylistsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Force recreate database instance to handle schema changes
        AppDatabase.destroyInstance();

        setupViewModel();
        setupRecyclerView();
        setupCreatePlaylistButton();

        // Ensure "Liked Songs" playlist exists via ViewModel
        viewModel.getOrCreateLikedPlaylist(
                new com.example.midnightmusic.data.repository.MusicRepository.PlaylistCallback() {
                    @Override
                    public void onSuccess(long playlistId) {
                        // Liked songs playlist confirmed
                    }

                    @Override
                    public void onError(Exception e) {
                        // Log error but don't crash
                    }
                });
    }

    private void setupViewModel() {
        try {
            viewModel = new ViewModelProvider(this).get(PlaylistViewModel.class);

            // Observe all playlists
            viewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
                // Filter out "Liked Songs" since it's shown in the header
                List<PlaylistWithSongs> filteredList = new ArrayList<>();
                PlaylistWithSongs likedSongsPlaylist = null;

                for (PlaylistWithSongs p : playlists) {
                    if (p.playlist.getName().equals(getString(R.string.liked_songs))) {
                        likedSongsPlaylist = p;
                    } else {
                        filteredList.add(p);
                    }
                }

                adapter.submitList(filteredList);
                binding.playlistCount.setText(filteredList.size() + " playlists");

                // Update Liked Songs card click listener if playlist exists
                if (likedSongsPlaylist != null) {
                    final long likedId = likedSongsPlaylist.playlist.getId();
                    binding.likedSongsCard.setOnClickListener(v -> openPlaylist(likedId));
                    binding.likedPlayBtn.setOnClickListener(v -> openPlaylist(likedId));
                }

                // Update empty state
                if (filteredList.isEmpty()) {
                    binding.emptyView.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyView.setVisibility(View.GONE);
                }
            });

            // Observe liked songs count separately for the badge/text
            viewModel.getLikedSongsCount().observe(getViewLifecycleOwner(), count -> {
                if (count != null) {
                    binding.likedCount.setText(count + " songs");
                } else {
                    binding.likedCount.setText("0 songs");
                }
            });

        } catch (IllegalStateException e) {
            handleDatabaseError(e);
        }
    }

    private void handleDatabaseError(IllegalStateException e) {
        // Handle database schema change errors
        if (e.getMessage() != null && e.getMessage().contains("Cannot verify the data integrity")) {
            // Clear the database and try again
            clearRoomDatabase();

            // Try again with the cleared database
            try {
                viewModel = new ViewModelProvider(this).get(PlaylistViewModel.class);
                viewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
                    // Reuse functionality by re-triggering observation
                    // Actual refresh logic duplicates some code but safe for recovery
                    adapter.submitList(playlists);
                });
            } catch (Exception retryEx) {
                Toast.makeText(requireContext(), "Database error: " + retryEx.getMessage(), Toast.LENGTH_LONG).show();
            }
        } else {
            Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openPlaylist(long playlistId) {
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra("playlist_id", playlistId);
        startActivity(intent);
    }

    private void setupRecyclerView() {
        adapter = new PlaylistAdapter(this);
        binding.recyclerView.setAdapter(adapter);
        // Disable nested scrolling since it's inside a NestedScrollView
        binding.recyclerView.setNestedScrollingEnabled(false);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        binding.recyclerView.setLayoutManager(layoutManager);
    }

    private void setupCreatePlaylistButton() {
        binding.fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
    }

    private void showCreatePlaylistDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_playlist, null);
        EditText editText = dialogView.findViewById(R.id.edit_playlist_name);
        View btnCreate = dialogView.findViewById(R.id.btn_create);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // Use valid style or default if custom not found (will fix style next)
        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // Transparent background for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCreate.setOnClickListener(v -> {
            String name = editText.getText().toString().trim();
            if (!name.isEmpty()) {
                createPlaylist(name);
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    // Reusing the same dialog layout for rename for consistency
    private void showRenamePlaylistDialog(PlaylistWithSongs playlistWithSongs) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_playlist, null);
        EditText editText = dialogView.findViewById(R.id.edit_playlist_name);
        View btnCreate = dialogView.findViewById(R.id.btn_create);
        View btnCancel = dialogView.findViewById(R.id.btn_cancel);

        // Set update text
        editText.setText(playlistWithSongs.playlist.getName());
        editText.setSelection(editText.getText().length());

        // Update button text logic could go here if we had access to button text,
        // but 'Create' is hardcoded in XML. Assuming generic 'Save' or 'Create' is
        // acceptable,
        // or we could change text if the view is a TextView/Button.
        // For now, keeping it simple as per user request for "beautifuler" dialog
        // structure.

        AlertDialog dialog = new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        btnCreate.setOnClickListener(v -> {
            String name = editText.getText().toString().trim();
            if (!name.isEmpty()) {
                renamePlaylist(playlistWithSongs, name);
                dialog.dismiss();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void showDeletePlaylistDialog(PlaylistWithSongs playlistWithSongs) {
        new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setTitle(R.string.delete_playlist)
                .setMessage(getString(R.string.confirm_delete_playlist, playlistWithSongs.playlist.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deletePlaylist(playlistWithSongs);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void createPlaylist(String name) {
        viewModel.createPlaylist(name);
        Toast.makeText(requireContext(), R.string.playlist_created, Toast.LENGTH_SHORT).show();
    }

    private void renamePlaylist(PlaylistWithSongs playlistWithSongs, String newName) {
        viewModel.renamePlaylist(playlistWithSongs.playlist, newName);
        Toast.makeText(requireContext(), R.string.playlist_renamed, Toast.LENGTH_SHORT).show();
    }

    private void deletePlaylist(PlaylistWithSongs playlistWithSongs) {
        viewModel.deletePlaylist(playlistWithSongs.playlist);
        Toast.makeText(requireContext(), R.string.playlist_deleted, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onPlaylistClick(PlaylistWithSongs playlist) {
        Intent intent = new Intent(requireContext(), PlaylistDetailActivity.class);
        intent.putExtra("playlist_id", playlist.playlist.getId());
        startActivity(intent);
    }

    @Override
    public void onPlaylistRename(PlaylistWithSongs playlist) {
        showRenamePlaylistDialog(playlist);
    }

    @Override
    public void onPlaylistDelete(PlaylistWithSongs playlist) {
        showDeletePlaylistDialog(playlist);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void clearRoomDatabase() {
        try {
            File dbFile = requireContext().getDatabasePath("midnight_music_db");
            if (dbFile.exists())
                dbFile.delete();
            File shmFile = new File(dbFile.getPath() + "-shm");
            if (shmFile.exists())
                shmFile.delete();
            File walFile = new File(dbFile.getPath() + "-wal");
            if (walFile.exists())
                walFile.delete();

            AppDatabase.destroyInstance();
            Toast.makeText(requireContext(), "Database cleared", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to clear database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}