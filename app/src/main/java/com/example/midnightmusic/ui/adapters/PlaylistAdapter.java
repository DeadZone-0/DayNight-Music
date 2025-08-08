package com.example.midnightmusic.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.midnightmusic.R;
import com.example.midnightmusic.data.model.PlaylistWithSongs;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Random;

public class PlaylistAdapter extends ListAdapter<PlaylistWithSongs, PlaylistAdapter.PlaylistViewHolder> {

    private final PlaylistListener listener;
    private static final int[] PLACEHOLDER_COLORS = {
            Color.parseColor("#000000"), // black
           
    };

    public interface PlaylistListener {
        void onPlaylistClick(PlaylistWithSongs playlist);
        void onPlaylistRename(PlaylistWithSongs playlist);
        void onPlaylistDelete(PlaylistWithSongs playlist);
    }

    public PlaylistAdapter(PlaylistListener listener) {
        super(new PlaylistDiffCallback());
        this.listener = listener;
    }

    @NonNull
    @Override
    public PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_playlist, parent, false);
        return new PlaylistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlaylistViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private final MaterialCardView cardView;
        private final ImageView playlistImage;
        private final TextView playlistTitle;
        private final TextView playlistSongs;
        private final TextView playlistInitials;
        private final ImageButton menuButton;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            cardView = itemView.findViewById(R.id.playlist_card);
            playlistImage = itemView.findViewById(R.id.playlist_image);
            playlistTitle = itemView.findViewById(R.id.playlist_title);
            playlistSongs = itemView.findViewById(R.id.playlist_songs);
            playlistInitials = itemView.findViewById(R.id.playlist_initials);
            menuButton = itemView.findViewById(R.id.playlist_menu);

            itemView.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onPlaylistClick(getItem(position));
                }
            });

            menuButton.setOnClickListener(v -> showPopupMenu(v, getBindingAdapterPosition()));
        }

        void bind(PlaylistWithSongs playlistWithSongs) {
            if (playlistWithSongs == null) return;

            playlistTitle.setText(playlistWithSongs.playlist.getName());
            playlistSongs.setText(playlistWithSongs.getFormattedSongCount());

            // Special style for "Liked Songs" playlist
            boolean isLikedPlaylist = playlistWithSongs.playlist.getName().equals("Liked Songs");
            if (isLikedPlaylist) {
                menuButton.setVisibility(View.INVISIBLE); // Can't delete or rename Liked Songs
                // Use a heart icon for Liked Songs
                playlistImage.setVisibility(View.INVISIBLE);
                playlistInitials.setVisibility(View.VISIBLE);
                playlistInitials.setText("🤍"); // Heart symbol
                playlistInitials.setTextSize(48); // Bigger text for heart
                // Set a gradient background for Liked Songs
                cardView.setCardBackgroundColor(Color.parseColor("#8A2BE2")); // Purple color
            } else {
                menuButton.setVisibility(View.VISIBLE);
                
                List<String> imageUrls = playlistWithSongs.getImageUrls(4);
                if (imageUrls != null && !imageUrls.isEmpty()) {
                    playlistImage.setVisibility(View.VISIBLE);
                    playlistInitials.setVisibility(View.GONE);
                    
                    // Load first image or implement collage in a more complex view
                    Glide.with(itemView.getContext())
                        .load(imageUrls.get(0))
                        .apply(RequestOptions.centerCropTransform())
                        .placeholder(R.drawable.placeholder_album)
                        .into(playlistImage);
                } else {
                    // Show initials for empty playlists
                    playlistImage.setVisibility(View.INVISIBLE);
                    playlistInitials.setVisibility(View.VISIBLE);
                    
                    String name = playlistWithSongs.playlist.getName();
                    String initials = getInitials(name);
                    playlistInitials.setText(initials);
                    playlistInitials.setTextSize(32); // Reset text size
                    
                    // Use consistent color based on playlist name
                    int colorIndex = Math.abs(name.hashCode()) % PLACEHOLDER_COLORS.length;
                    cardView.setCardBackgroundColor(PLACEHOLDER_COLORS[colorIndex]);
                }
            }
        }

        private void showPopupMenu(View view, int position) {
            if (position == RecyclerView.NO_POSITION) return;
            
            Context context = view.getContext();
            PlaylistWithSongs playlist = getItem(position);
            
            PopupMenu popup = new PopupMenu(context, view);
            popup.inflate(R.menu.menu_playlist_options);
            
            // Don't allow deleting Liked Songs playlist
            if (playlist.playlist.getName().equals("Liked Songs")) {
                popup.getMenu().findItem(R.id.action_delete_playlist).setVisible(false);
                popup.getMenu().findItem(R.id.action_rename_playlist).setVisible(false);
            }
            
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == R.id.action_rename_playlist) {
                    listener.onPlaylistRename(playlist);
                    return true;
                } else if (id == R.id.action_delete_playlist) {
                    listener.onPlaylistDelete(playlist);
                    return true;
                }
                return false;
            });
            
            popup.show();
        }
        
        private String getInitials(String text) {
            if (text == null || text.isEmpty()) return "?";
            
            StringBuilder initials = new StringBuilder();
            String[] words = text.split("\\s+");
            int count = Math.min(3, words.length);
            
            for (int i = 0; i < count; i++) {
                if (!words[i].isEmpty()) {
                    initials.append(words[i].charAt(0));
                }
            }
            
            return initials.toString().toUpperCase();
        }
    }

    private static class PlaylistDiffCallback extends DiffUtil.ItemCallback<PlaylistWithSongs> {
        @Override
        public boolean areItemsTheSame(@NonNull PlaylistWithSongs oldItem, @NonNull PlaylistWithSongs newItem) {
            return oldItem.playlist.getId() == newItem.playlist.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull PlaylistWithSongs oldItem, @NonNull PlaylistWithSongs newItem) {
            return oldItem.playlist.getName().equals(newItem.playlist.getName()) &&
                   oldItem.getSongCount() == newItem.getSongCount();
        }
    }
} 