package com.midnight.music.ui.artist;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.data.model.Song;

import java.util.Objects;
import com.midnight.music.utils.ImageLoader;
import com.google.android.material.imageview.ShapeableImageView;

public class ArtistSongAdapter extends ListAdapter<Song, ArtistSongAdapter.SongViewHolder> {

    private final SongClickListener listener;

    public interface SongClickListener {
        void onSongClick(Song song);
        void onPlayClick(Song song, int position);
    }

    public ArtistSongAdapter(SongClickListener listener) {
        super(new DiffUtil.ItemCallback<Song>() {
            @Override
            public boolean areItemsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Song oldItem, @NonNull Song newItem) {
                return Objects.equals(oldItem.getSong(), newItem.getSong())
                        && Objects.equals(oldItem.getImageUrl(), newItem.getImageUrl())
                        && Objects.equals(oldItem.getAlbum(), newItem.getAlbum())
                        && Objects.equals(oldItem.getSingers(), newItem.getSingers())
                        && oldItem.isLiked() == newItem.isLiked();
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_artist_song, parent, false);
        return new SongViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, int position) {
        holder.bind(getItem(position), position);
    }

    class SongViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView songImage;
        private final TextView songName;
        private final TextView songInfo;
        private final ImageButton playButton;

        SongViewHolder(@NonNull View itemView) {
            super(itemView);
            songImage = itemView.findViewById(R.id.song_image);
            songName = itemView.findViewById(R.id.song_name);
            songInfo = itemView.findViewById(R.id.song_info);
            playButton = itemView.findViewById(R.id.play_button);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onSongClick(getItem(pos));
                }
            });

            playButton.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onPlayClick(getItem(pos), pos);
                }
            });
        }

        void bind(Song song, int position) {
            String title = (position + 1) + ". " + song.getSong();
            songName.setText(title);
            
            String info = song.getAlbum();
            if (info != null && !info.isEmpty()) {
                songInfo.setText(info);
            } else {
                songInfo.setText(song.getSingers());
            }

            String imageUrl = song.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageLoader.loadImage(songImage, imageUrl);
            } else {
                songImage.setImageResource(R.drawable.placeholder_song);
            }
        }
    }
}