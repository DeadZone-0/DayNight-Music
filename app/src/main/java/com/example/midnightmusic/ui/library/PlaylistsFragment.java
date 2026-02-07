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
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.data.db.AppDatabase;
import com.example.midnightmusic.data.model.Playlist;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.databinding.FragmentPlaylistsBinding;
import com.example.midnightmusic.ui.adapters.PlaylistAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.midnightmusic.ui.playlist.PlaylistDetailActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlaylistsFragment extends Fragment implements PlaylistAdapter.PlaylistListener {
    private FragmentPlaylistsBinding binding;
    private PlaylistAdapter adapter;
    private PlaylistViewModel viewModel;
    private final Executor executor = Executors.newSingleThreadExecutor();

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
        setupFab();
        
        // Ensure "Liked Songs" playlist exists
        ensureLikedSongsPlaylist();
    }

    private void setupViewModel() {
        try {
            viewModel = new ViewModelProvider(this).get(PlaylistViewModel.class);
            viewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
                adapter.submitList(playlists);
                
                // Update empty state
                if (playlists.isEmpty()) {
                    binding.emptyView.setVisibility(View.VISIBLE);
                } else {
                    binding.emptyView.setVisibility(View.GONE);
                }
            });
        } catch (IllegalStateException e) {
            // Handle database schema change errors
            if (e.getMessage() != null && e.getMessage().contains("Cannot verify the data integrity")) {
                // Clear the database and try again
                clearRoomDatabase();
                
                // Try again with the cleared database
                try {
                    viewModel = new ViewModelProvider(this).get(PlaylistViewModel.class);
                    viewModel.getAllPlaylists().observe(getViewLifecycleOwner(), playlists -> {
                        adapter.submitList(playlists);
                        
                        // Update empty state
                        if (playlists.isEmpty()) {
                            binding.emptyView.setVisibility(View.VISIBLE);
                        } else {
                            binding.emptyView.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception retryEx) {
                    Toast.makeText(requireContext(), "Database error: " + retryEx.getMessage(), Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupRecyclerView() {
        adapter = new PlaylistAdapter(this);
        binding.recyclerView.setAdapter(adapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext(), LinearLayoutManager.VERTICAL, false);
        binding.recyclerView.setLayoutManager(layoutManager);
    }
    
    private void setupFab() {
        binding.fabCreatePlaylist.setOnClickListener(v -> showCreatePlaylistDialog());
    }
    
    private void ensureLikedSongsPlaylist() {
        executor.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(requireContext());
            boolean exists = db.playlistDao().isPlaylistExists(getString(R.string.liked_songs));
            if (!exists) {
                Playlist likedSongs = new Playlist(getString(R.string.liked_songs));
                db.playlistDao().insert(likedSongs);
            }
        });
    }

    private void showCreatePlaylistDialog() {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_playlist, null);
        EditText editText = dialogView.findViewById(R.id.edit_playlist_name);

        new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setTitle(R.string.create_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = editText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createPlaylist(name);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
    
    private void showRenamePlaylistDialog(PlaylistWithSongs playlistWithSongs) {
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_create_playlist, null);
        EditText editText = dialogView.findViewById(R.id.edit_playlist_name);
        editText.setText(playlistWithSongs.playlist.getName());
        editText.setSelection(editText.getText().length());

        new AlertDialog.Builder(requireContext(), R.style.PlaylistDialogStyle)
                .setTitle(R.string.rename_playlist)
                .setView(dialogView)
                .setPositiveButton(R.string.rename, (dialog, which) -> {
                    String name = editText.getText().toString().trim();
                    if (!name.isEmpty()) {
                        renamePlaylist(playlistWithSongs, name);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
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
            // Get the database file
            File dbFile = requireContext().getDatabasePath("midnight_music_db");
            if (dbFile.exists()) {
                dbFile.delete();
            }
            
            // Also delete the -shm and -wal files if they exist
            File shmFile = new File(dbFile.getPath() + "-shm");
            if (shmFile.exists()) {
                shmFile.delete();
            }
            
            File walFile = new File(dbFile.getPath() + "-wal");
            if (walFile.exists()) {
                walFile.delete();
            }
            
            // Force instance recreation
            AppDatabase.destroyInstance();
            
            Toast.makeText(requireContext(), "Database cleared", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Failed to clear database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
} 