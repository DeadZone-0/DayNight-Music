package com.midnight.music.ui.search;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.data.model.Artist;
import com.midnight.music.utils.ImageLoader;
import com.google.android.material.imageview.ShapeableImageView;

public class ArtistAdapter extends ListAdapter<Artist, ArtistAdapter.ArtistViewHolder> {

    private final ArtistClickListener listener;

    public interface ArtistClickListener {
        void onArtistClick(Artist artist);
    }

    public ArtistAdapter(ArtistClickListener listener) {
        super(new DiffUtil.ItemCallback<Artist>() {
            @Override
            public boolean areItemsTheSame(@NonNull Artist oldItem, @NonNull Artist newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Artist oldItem, @NonNull Artist newItem) {
                return oldItem.getName().equals(newItem.getName());
            }
        });
        this.listener = listener;
    }

    @NonNull
    @Override
    public ArtistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_artist_card, parent, false);
        return new ArtistViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ArtistViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ArtistViewHolder extends RecyclerView.ViewHolder {
        private final ShapeableImageView artistImage;
        private final TextView artistName;

        ArtistViewHolder(@NonNull View itemView) {
            super(itemView);
            artistImage = itemView.findViewById(R.id.artist_image);
            artistName = itemView.findViewById(R.id.artist_name);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onArtistClick(getItem(pos));
                }
            });
        }

        void bind(Artist artist) {
            artistName.setText(artist.getName());
            String imageUrl = artist.getImageUrl();
            if (imageUrl != null && !imageUrl.isEmpty()) {
                ImageLoader.loadImage(artistImage, imageUrl);
            } else {
                artistImage.setImageResource(R.drawable.ic_artist_placeholder);
            }
        }
    }
}