package com.example.midnightmusic.ui.library;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LibraryPagerAdapter extends FragmentStateAdapter {

    public LibraryPagerAdapter(@NonNull Fragment fragment) {
        super(fragment);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return new PlaylistsFragment();
            case 1:
                return new DownloadsFragment();
            default:
                throw new IllegalStateException("Invalid position " + position);
        }
    }

    @Override
    public int getItemCount() {
        return 2; // Only Playlists and Downloads
    }
} 