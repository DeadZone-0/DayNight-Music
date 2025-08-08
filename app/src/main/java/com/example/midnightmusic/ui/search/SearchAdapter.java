package com.example.midnightmusic.ui.search;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.Song;
import com.example.midnightmusic.utils.ImageLoader;
import com.google.android.material.imageview.ShapeableImageView;

public class SearchAdapter extends ListAdapter<Song, SearchAdapter.SongViewHolder> {

    private final SearchAdapterListener listener;

    public interface SearchAdapterListener {
        void onSongClick(Song song);
        void onPlayNow(Song song);
        void onAddToPlaylist(Song song);
        void onQueueNext(Song song);
        void onToggleLike(Song song);
    }

    public SearchAdapter(SearchAdapterListener listener) {
        super(new SongDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_search_result, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView songImage;
        private final TextView songName;
        private final TextView songInfo;
        private final ImageButton moreOptions;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            songImage = itemView.findViewById(R.id.song_image);
            songName = itemView.findViewById(R.id.song_name);
            songInfo = itemView.findViewById(R.id.song_info);
            moreOptions = itemView.findViewById(R.id.more_options);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onSongClick(getItem(position));
                }
            });

            moreOptions.setOnClickListener(this::showPopupMenu);
        }

        void bind(Song song) {
            songName.setText(song.getSong());
            songInfo.setText(String.format("%s • %s • %s",
                    song.getSingers(),
                    song.getAlbum(),
                    formatDuration(song.getDuration())));

            String imageUrl = song.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageLoader.loadImage(songImage, imageUrl);
            }
        }

        private void showPopupMenu(View view) {
            PopupMenu popup = new PopupMenu(view.getContext(), view);
            popup.inflate(R.menu.menu_song_options);
            
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                Song song = getItem(position);
                
                // Set the correct text and icon for the like/unlike menu item
                MenuItem likeItem = popup.getMenu().findItem(R.id.action_like);
                updateLikeMenuItem(likeItem, song.isLiked());
                
                popup.setOnMenuItemClickListener(item -> {
                    int itemId = item.getItemId();
                    if (itemId == R.id.action_play_now) {
                        listener.onPlayNow(song);
                        return true;
                    } else if (itemId == R.id.action_add_to_playlist) {
                        listener.onAddToPlaylist(song);
                        return true;
                    } else if (itemId == R.id.action_queue_next) {
                        listener.onQueueNext(song);
                        return true;
                    } else if (itemId == R.id.action_like) {
                        // Toggle liked status and notify listener
                        listener.onToggleLike(song);
                        
                        // Update this menu item right away
                        updateLikeMenuItem(item, song.isLiked());
                        return true;
                    }
                    return false;
                });
            }
            
            popup.show();
        }
        
        /**
         * Updates like menu item text and icon based on current liked status
         */
        private void updateLikeMenuItem(MenuItem item, boolean isLiked) {
            if (isLiked) {
                item.setTitle(R.string.unlike);
                item.setIcon(R.drawable.ic_heart_filled);
            } else {
                item.setTitle(R.string.like);
                item.setIcon(R.drawable.ic_heart);
            }
        }

        private String formatDuration(String durationStr) {
            try {
                int duration = Integer.parseInt(durationStr);
                int minutes = duration / 60;
                int seconds = duration % 60;
                return String.format("%d:%02d", minutes, seconds);
            } catch (NumberFormatException e) {
                return durationStr;
            }
        }
    }

    static class SongDiffCallback extends DiffUtil.ItemCallback<Song> {
        @Override
        public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            // Items are the same if they have the same ID
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
            // Check if any important properties have changed
            return oldItem.getSong().equals(newItem.getSong())
                    && oldItem.getSingers().equals(newItem.getSingers())
                    && oldItem.getImageUrl().equals(newItem.getImageUrl())
                    && oldItem.isLiked() == newItem.isLiked();  // Check liked status too
        }
    }
} 