package com.midnight.music.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.midnight.music.R;
import com.midnight.music.data.model.PlaylistWithSongs;
import com.midnight.music.data.model.Song;
import com.midnight.music.databinding.FragmentHomeBinding;
import com.midnight.music.player.MusicPlayerManager;
import com.midnight.music.ui.adapters.PlaylistTileAdapter;
import com.midnight.music.ui.adapters.RecentCompactAdapter;
import com.midnight.music.ui.adapters.SongCardAdapter;
import com.midnight.music.ui.player.PlayerActivity;
import com.midnight.music.utils.ThemeManager;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements
        PlaylistTileAdapter.OnPlaylistClickListener,
        SongCardAdapter.OnSongClickListener,
        RecentCompactAdapter.OnSongClickListener {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private PlaylistTileAdapter recentPlaylistsAdapter;
    private RecentCompactAdapter recentlyPlayedAdapter;
    private SongCardAdapter recommendedAdapter;
    private SongCardAdapter trendingAdapter;

    private List<Song> recentlyPlayedSongs = new ArrayList<>();
    private List<Song> recommendedSongs = new ArrayList<>();
    private List<Song> trendingSongs = new ArrayList<>();

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
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);
        setupHeader();
        setupAnimezePromo();
        setupRecyclerViews();
        observeData();

        // Observe accent colour and tint header gradient
        ThemeManager.getInstance(requireContext()).getAccentColor().observe(getViewLifecycleOwner(), this::applyAccentToHeader);
    }

    private void setupAnimezePromo() {
        binding.cardAnimezePromo.setOnClickListener(v -> {
            try {
                Intent launchIntent = requireContext().getPackageManager().getLaunchIntentForPackage("com.animeze.app");
                if (launchIntent != null) {
                    startActivity(launchIntent);
                } else {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://animeze.bond"));
                    startActivity(webIntent);
                }
            } catch (Exception e) {
                Toast.makeText(requireContext(), "Animeze: https://animeze.bond", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyAccentToHeader(int color) {
        if (binding == null) return;
        int startColor = Color.argb(64, Color.red(color), Color.green(color), Color.blue(color)); // 25% alpha
        int centerColor = Color.argb(32, Color.red(color), Color.green(color), Color.blue(color)); // 12% alpha
        int endColor = Color.TRANSPARENT;
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TR_BL,
                new int[]{startColor, centerColor, endColor});
        gd.setShape(GradientDrawable.RECTANGLE);
        binding.headerContainer.setBackground(gd);
    }

    private void setupHeader() {
        viewModel.getGreeting().observe(getViewLifecycleOwner(), greeting -> binding.greetingText.setText(greeting));

        binding.btnNotifications
                .setOnClickListener(v -> Toast.makeText(requireContext(), "Notifications", Toast.LENGTH_SHORT).show());

        binding.btnHistory.setOnClickListener(v -> {
            startActivity(new android.content.Intent(requireContext(), com.midnight.music.ui.history.HistoryActivity.class));
        });

        binding.btnSettings.setOnClickListener(v -> {
            startActivity(new android.content.Intent(requireContext(), com.midnight.music.ui.settings.SettingsActivity.class));
        });
    }

    private void setupRecyclerViews() {
        // Playlists grid
        binding.recentPlaylistsRecycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recentPlaylistsRecycler.setNestedScrollingEnabled(false);
        binding.recentPlaylistsRecycler.setHasFixedSize(true);
        binding.recentPlaylistsRecycler.setItemViewCacheSize(8);
        recentPlaylistsAdapter = new PlaylistTileAdapter(new ArrayList<>(), this);
        binding.recentPlaylistsRecycler.setAdapter(recentPlaylistsAdapter);

        // Recently played (compact horizontal)
        binding.recentlyPlayedRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recentlyPlayedRecycler.setNestedScrollingEnabled(false);
        binding.recentlyPlayedRecycler.setHasFixedSize(true);
        binding.recentlyPlayedRecycler.setItemViewCacheSize(10);
        recentlyPlayedAdapter = new RecentCompactAdapter(new ArrayList<>(), this);
        binding.recentlyPlayedRecycler.setAdapter(recentlyPlayedAdapter);

        // Recommended for you (horizontal cards)
        binding.recommendedRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recommendedRecycler.setNestedScrollingEnabled(false);
        binding.recommendedRecycler.setHasFixedSize(true);
        binding.recommendedRecycler.setItemViewCacheSize(10);
        recommendedAdapter = new SongCardAdapter(new ArrayList<>(), this);
        binding.recommendedRecycler.setAdapter(recommendedAdapter);

        // Trending (horizontal cards)
        binding.newReleasesRecycler.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.newReleasesRecycler.setNestedScrollingEnabled(false);
        binding.newReleasesRecycler.setHasFixedSize(true);
        binding.newReleasesRecycler.setItemViewCacheSize(10);
        trendingAdapter = new SongCardAdapter(new ArrayList<>(), this);
        binding.newReleasesRecycler.setAdapter(trendingAdapter);
    }

    private void observeData() {
        // Playlists
        viewModel.getPlaylists().observe(getViewLifecycleOwner(), playlists -> {
            setVisible(binding.shimmerPlaylists, false);
            if (playlists != null && !playlists.isEmpty()) {
                List<PlaylistWithSongs> limited = playlists.size() > 4
                        ? playlists.subList(0, 4)
                        : playlists;
                recentPlaylistsAdapter.updateData(limited);
                setVisible(binding.recentPlaylistsTitle, true);
                setVisible(binding.recentPlaylistsSubtitle, true);
                setVisible(binding.recentPlaylistsRecycler, true);
            } else {
                setVisible(binding.recentPlaylistsTitle, false);
                setVisible(binding.recentPlaylistsSubtitle, false);
                setVisible(binding.recentPlaylistsRecycler, false);
            }
        });

        // Recently played (compact)
        viewModel.getRecentlyPlayedSongs().observe(getViewLifecycleOwner(), songs -> {
            setVisible(binding.shimmerRecentlyPlayed, false);
            if (songs != null && !songs.isEmpty()) {
                recentlyPlayedSongs = songs;
                recentlyPlayedAdapter.updateData(songs);
                setVisible(binding.recentlyPlayedTitle, true);
                setVisible(binding.recentlyPlayedRecycler, true);

                // Load recommendations from multiple recent songs
                viewModel.loadRecommendations(songs);
            } else {
                setVisible(binding.recentlyPlayedTitle, false);
                setVisible(binding.recentlyPlayedRecycler, false);
                
                // Signal empty recommendations when there is no recent history
                viewModel.loadRecommendations(new ArrayList<>());
            }
        });

        // Recommended for you
        viewModel.getRecommendations().observe(getViewLifecycleOwner(), songs -> {
            setVisible(binding.shimmerRecommended, false);
            if (songs != null && !songs.isEmpty()) {
                recommendedSongs = songs;
                recommendedAdapter.updateData(songs);
                setVisible(binding.recommendedTitle, true);
                setVisible(binding.recommendedSubtitle, true);
                setVisible(binding.recommendedRecycler, true);
                setVisible(binding.recommendedEmptyText, false);
            } else {
                setVisible(binding.recommendedTitle, true);
                setVisible(binding.recommendedSubtitle, false);
                setVisible(binding.recommendedRecycler, false);
                setVisible(binding.recommendedEmptyText, true);
            }
        });

        // Trending
        viewModel.getTrending().observe(getViewLifecycleOwner(), songs -> {
            setVisible(binding.shimmerNewReleases, false);
            if (songs != null && !songs.isEmpty()) {
                trendingSongs = songs;
                trendingAdapter.updateData(songs);
                updateHeroSpotlight(songs.get(0));
                setVisible(binding.newReleasesTitle, true);
                setVisible(binding.newReleasesSubtitle, true);
                setVisible(binding.newReleasesRecycler, true);
                setVisible(binding.trendingErrorState, false);
            } else if (songs == null || songs.isEmpty()) {
                setVisible(binding.newReleasesTitle, true);
                setVisible(binding.newReleasesSubtitle, false);
                setVisible(binding.newReleasesRecycler, false);
                setVisible(binding.trendingErrorState, true);
            }
        });
    }

    private void updateHeroSpotlight(Song song) {
        if (song == null || binding == null) return;
        binding.heroSongTitle.setText(song.getSong());
        binding.heroSongArtist.setText(song.getSingers() != null ? song.getSingers() : song.getAlbum());

        String imgUrl = song.getImageUrl();
        if (imgUrl != null && !imgUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(imgUrl)
                    .placeholder(R.drawable.placeholder_album)
                    .error(R.drawable.placeholder_album)
                    .centerCrop()
                    .into(binding.heroBackdropImage);
        }

        binding.heroPlayBtn.setOnClickListener(v -> playSongFromList(song));
        binding.heroSpotlightCard.setOnClickListener(v -> playSongFromList(song));
    }

    // RecentCompactAdapter.OnSongClickListener
    @Override
    public void onSongClick(Song song) {
        playSongFromList(song);
    }

    // SongCardAdapter.OnSongClickListener
    // (both Recommended and Trending adapters use this)
    // Handled via the same method name

    @Override
    public void onPlaylistClick(PlaylistWithSongs playlist) {
        if (playlist != null && playlist.playlist != null) {
            Intent intent = new Intent(requireContext(), com.midnight.music.ui.playlist.PlaylistDetailActivity.class);
            intent.putExtra("playlist_id", playlist.playlist.getId());
            startActivity(intent);
        }
    }

    private void playSongFromList(Song song) {
        if (song == null || song.getMediaUrl() == null) {
            Toast.makeText(requireContext(), "Song not available", Toast.LENGTH_SHORT).show();
            return;
        }

        MusicPlayerManager playerManager = MusicPlayerManager.getInstance(requireContext());

        // Check which section this song belongs to
        int recIndex = findSongIndex(recommendedSongs, song);
        if (recIndex >= 0) {
            // Recommended songs behave like search â€” auto-queue enabled
            playerManager.playQueue(recommendedSongs, recIndex);
            playerManager.setAutoQueueEnabled(true); // AFTER playQueue (which resets it)
        } else {
            // Recently played, trending, playlists â€” no auto-queue
            playerManager.setAutoQueueEnabled(false);

            int index = findSongIndex(recentlyPlayedSongs, song);
            if (index >= 0) {
                playerManager.playQueue(recentlyPlayedSongs, index);
            } else {
                index = findSongIndex(trendingSongs, song);
                if (index >= 0) {
                    playerManager.playQueue(trendingSongs, index);
                } else {
                    playerManager.playSong(song);
                }
            }
        }

        Intent intent = new Intent(requireContext(), PlayerActivity.class);
        startActivity(intent);
    }

    private int findSongIndex(List<Song> list, Song song) {
        if (list == null || song == null)
            return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(song.getId()))
                return i;
        }
        return -1;
    }

    private void setVisible(View view, boolean visible) {
        if (view == null) return;
        view.animate().cancel();
        if (visible) {
            view.setVisibility(View.VISIBLE);
            if (view.getAlpha() < 1f) {
                view.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator())
                        .start();
            }
        } else {
            if (view.getVisibility() == View.VISIBLE) {
                view.animate()
                        .alpha(0f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            view.setVisibility(View.GONE);
                        })
                        .start();
            } else {
                view.setVisibility(View.GONE);
                view.setAlpha(0f);
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
