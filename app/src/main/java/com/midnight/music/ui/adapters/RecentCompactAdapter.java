package com.midnight.music.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.midnight.music.R;
import com.midnight.music.data.model.Song;

import java.util.List;

/**
 * Compact adapter for recently played songs â€” smaller cards (120dp).
 */
public class RecentCompactAdapter extends RecyclerView.Adapter<RecentCompactAdapter.ViewHolder> {
    private List<Song> songs;
    private final OnSongClickListener listener;

    public interface OnSongClickListener {
        void onSongClick(Song song);
    }

    public RecentCompactAdapter(List<Song> songs, OnSongClickListener listener) {
        this.songs = songs;
        this.listener = listener;
    }

    public void updateData(List<Song> newSongs) {
        this.songs = newSongs;
        notifyDataSetChanged();
    }

    public Song getSongAt(int position) {
        if (position >= 0 && position < songs.size()) return songs.get(position);
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recent_compact, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(songs.get(position));
    }

    @Override
    public int getItemCount() {
        return songs != null ? songs.size() : 0;
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView songImage;
        private final TextView songTitle;
        private final TextView songArtist;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            songImage = itemView.findViewById(R.id.song_image);
            songTitle = itemView.findViewById(R.id.song_title);
            songArtist = itemView.findViewById(R.id.song_artist);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSongClick(songs.get(pos));
                }
            });
        }

        void bind(Song song) {
            songTitle.setText(song.getSong());
            songArtist.setText(song.getSingers());

            String imageUrl = song.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(imageUrl)
                        .placeholder(R.drawable.placeholder_song)
                        .into(songImage);
            } else {
                songImage.setImageResource(R.drawable.placeholder_song);
            }
        }
    }
}
