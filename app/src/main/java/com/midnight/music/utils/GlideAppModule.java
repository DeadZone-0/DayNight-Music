package com.midnight.music.utils;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Global Glide configuration for DayNight Music.
 * Sets disk cache size, default decode format, and caching strategy.
 */
@GlideModule
public class GlideAppModule extends AppGlideModule {

    private static final int DISK_CACHE_SIZE = 250 * 1024 * 1024; // 250MB

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 250MB disk cache for album art and thumbnails
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, DISK_CACHE_SIZE));

        // Default request options: cache everything, prefer ARGB_8888 for quality
        builder.setDefaultRequestOptions(
                new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .format(DecodeFormat.PREFER_ARGB_8888)
        );
    }

    @Override
    public boolean isManifestParsingEnabled() {
        // Disable manifest parsing for faster initialization
        return false;
    }
}
