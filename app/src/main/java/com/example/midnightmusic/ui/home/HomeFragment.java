package com.example.midnightmusic.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.databinding.FragmentHomeBinding;
import com.example.midnightmusic.ui.adapters.PlaylistTileAdapter;
import com.example.midnightmusic.ui.adapters.SongCardAdapter;
import com.example.midnightmusic.ui.player.PlayerActivity;
import com.example.midnightmusic.ui.settings.SettingsFragment;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements PlaylistTileAdapter.OnPlaylistClickListener, SongCardAdapter.OnSongClickListener {
    private FragmentHomeBinding binding;
    private PlaylistTileAdapter recentPlaylistsAdapter;
    private SongCardAdapter recentlyPlayedAdapter;
    private SongCardAdapter newReleasesAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                           @Nullable ViewGroup container,
                           @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupHeader();
        setupRecyclerViews();
        loadData();
    }

    private void setupHeader() {
        updateGreeting();
        
        // Set click listeners for header icons
        binding.btnNotifications.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Notifications", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnHistory.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "History", Toast.LENGTH_SHORT).show();
        });
        
        binding.btnSettings.setOnClickListener(v -> {
            // Navigate to settings fragment using Navigation component
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.action_navigation_home_to_settingsFragment);
        });
    }

    private void setupRecyclerViews() {
        // Set up the recent playlists grid
        RecyclerView recentPlaylistsRecyclerView = binding.recentPlaylistsRecycler;
        GridLayoutManager gridLayoutManager = new GridLayoutManager(requireContext(), 2);
        gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return 1; // Each item takes 1 span (2 items per row)
            }
        });
        recentPlaylistsRecyclerView.setLayoutManager(gridLayoutManager);
        recentPlaylistsRecyclerView.setNestedScrollingEnabled(false);
        
        List<PlaylistWithSongs> emptyPlaylists = new ArrayList<>();
        recentPlaylistsAdapter = new PlaylistTileAdapter(emptyPlaylists, this);
        recentPlaylistsRecyclerView.setAdapter(recentPlaylistsAdapter);
        
        // Set up the recently played section
        RecyclerView recentlyPlayedRecyclerView = binding.recentlyPlayedRecycler;
        recentlyPlayedRecyclerView.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        recentlyPlayedRecyclerView.setNestedScrollingEnabled(false);
        
        List<Song> emptySongs = new ArrayList<>();
        recentlyPlayedAdapter = new SongCardAdapter(emptySongs, this);
        recentlyPlayedRecyclerView.setAdapter(recentlyPlayedAdapter);
        
        // Set up the new releases section
        RecyclerView newReleasesRecyclerView = binding.newReleasesRecycler;
        newReleasesRecyclerView.setLayoutManager(
            new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        newReleasesRecyclerView.setNestedScrollingEnabled(false);
        
        newReleasesAdapter = new SongCardAdapter(emptySongs, this);
        newReleasesRecyclerView.setAdapter(newReleasesAdapter);

        // Add scroll listener to handle item animations
        setupScrollListeners(recentlyPlayedRecyclerView);
        setupScrollListeners(newReleasesRecyclerView);
    }
    
    private void setupScrollListeners(RecyclerView recyclerView) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Hide all play buttons initially
                for (int i = 0; i < recyclerView.getChildCount(); i++) {
                    View child = recyclerView.getChildAt(i);
                    ImageView playButton = child.findViewById(R.id.btn_play);
                    if (playButton != null) {
                        playButton.setVisibility(View.GONE);
                    }
                }
                
                // Show play button for visible items
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null) {
                    int firstVisible = layoutManager.findFirstCompletelyVisibleItemPosition();
                    int lastVisible = layoutManager.findLastCompletelyVisibleItemPosition();
                    
                    for (int i = firstVisible; i <= lastVisible; i++) {
                        View view = layoutManager.findViewByPosition(i);
                        if (view != null) {
                            ImageView playButton = view.findViewById(R.id.btn_play);
                            if (playButton != null) {
                                playButton.setVisibility(View.VISIBLE);
                                
                                // Set click listener for play button
                                playButton.setOnClickListener(v -> {
                                    int position = recyclerView.getChildAdapterPosition(view);
                                    SongCardAdapter adapter = (SongCardAdapter) recyclerView.getAdapter();
                                    if (adapter != null && position != RecyclerView.NO_POSITION) {
                                        Song song = adapter.getSongAt(position);
                                        onSongClick(song);
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }
    
    private void loadData() {
        // Load data for all sections
        loadRecentPlaylists();
        loadRecentlyPlayedSongs();
        loadNewReleases();
    }
    
    private void loadRecentPlaylists() {
        // TODO: In the future, load this data from the JioSaavn API or local database
        List<PlaylistWithSongs> playlists = getRecentPlaylists();
        recentPlaylistsAdapter.updateData(playlists);
    }
    
    private void loadRecentlyPlayedSongs() {
        // TODO: In the future, load this data from the JioSaavn API or local database
        List<Song> songs = getRecentlyPlayedSongs();
        recentlyPlayedAdapter.updateData(songs);
    }
    
    private void loadNewReleases() {
        // TODO: In the future, load this data from the JioSaavn API or local database
        List<Song> songs = getNewReleases();
        newReleasesAdapter.updateData(songs);
    }

    private List<PlaylistWithSongs> getRecentPlaylists() {
        // Sample data for recent playlists - limit to 4 items (2x2 grid)
        List<PlaylistWithSongs> playlists = new ArrayList<>();
        
        for (int i = 1; i <= 4; i++) {
            PlaylistWithSongs playlistWithSongs = new PlaylistWithSongs();
            com.example.midnightmusic.data.model.Playlist playlist = 
                new com.example.midnightmusic.data.model.Playlist("Playlist " + i);
            playlist.setId(i);
            playlistWithSongs.playlist = playlist;
            
            // Add some sample songs to each playlist
            List<Song> songs = new ArrayList<>();
            for (int j = 1; j <= 5; j++) {
                Song song = new Song("song_" + i + "_" + j);
                song.setSong("Song " + j);
                song.setSingers("Artist " + j);
                song.setImageUrl("https://picsum.photos/200/200?random=" + (i*10 + j));
                songs.add(song);
            }
            playlistWithSongs.songs = songs;
            
            playlists.add(playlistWithSongs);
        }
        
        return playlists;
    }

    private List<Song> getRecentlyPlayedSongs() {
        // Sample data for recently played songs
        List<Song> songs = new ArrayList<>();
        
        for (int i = 1; i <= 10; i++) {
            Song song = new Song("recently_played_" + i);
            song.setSong("Recently Played " + i);
            song.setSingers("Artist " + i);
            song.setImageUrl("https://picsum.photos/200/200?random=" + (100 + i));
            songs.add(song);
        }
        
        return songs;
    }
    
    private List<Song> getNewReleases() {
        // Sample data for new releases
        List<Song> songs = new ArrayList<>();
        
        for (int i = 1; i <= 10; i++) {
            Song song = new Song("new_release_" + i);
            song.setSong("New Release " + i);
            song.setSingers("Artist " + i);
            song.setImageUrl("https://picsum.photos/200/200?random=" + (200 + i));
            songs.add(song);
        }
        
        return songs;
    }

    private void updateGreeting() {
        int hour = LocalTime.now().getHour();
        String greeting;
        
        if (hour >= 5 && hour < 12) {
            greeting = "Good morning";
        } else if (hour >= 12 && hour < 18) {
            greeting = "Good afternoon";
        } else {
            greeting = "Good evening";
        }
        
        binding.greetingText.setText(greeting);
    }

    @Override
    public void onPlaylistClick(PlaylistWithSongs playlist) {
        // Handle playlist click - can open playlist detail screen
        Toast.makeText(requireContext(), 
                      "Clicked playlist: " + playlist.playlist.getName(), 
                      Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onSongClick(Song song) {
        // Handle song click - can play the song or open player screen
        Toast.makeText(requireContext(), 
                      "Playing song: " + song.getSong(), 
                      Toast.LENGTH_SHORT).show();
        
        // Example: Open player activity
        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
} 