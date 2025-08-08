package com.example.midnightmusic.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.URL;

public class ImageLoader {
    // Memory cache for storing bitmaps
    private static final LruCache<String, Bitmap> memoryCache;
    
    static {
        // Get max available VM memory, exceeding this amount will throw an OutOfMemory exception.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;
        
        memoryCache = new LruCache<String, Bitmap>(cacheSize) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                // The cache size will be measured in kilobytes rather than number of items.
                return bitmap.getByteCount() / 1024;
            }
        };
    }
    
    // Add a bitmap to the memory cache
    private static void addBitmapToCache(String url, Bitmap bitmap) {
        if (getBitmapFromCache(url) == null && bitmap != null) {
            memoryCache.put(url, bitmap);
        }
    }
    
    // Get a bitmap from the memory cache
    private static Bitmap getBitmapFromCache(String url) {
        return memoryCache.get(url);
    }

    public static class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        private final ImageView imageView;
        private final String originalUrl;

        public LoadImageTask(ImageView imageView, String url) {
            this.imageView = imageView;
            this.originalUrl = url;
            // Set tag to current URL to prevent wrong image loading
            imageView.setTag(url);
        }

        @Override
        protected Bitmap doInBackground(String... urls) {
            try {
                String secureUrl = urls[0].replace("http://", "https://");
                
                // Check if the bitmap is already in cache
                Bitmap cachedBitmap = getBitmapFromCache(secureUrl);
                if (cachedBitmap != null) {
                    return cachedBitmap;
                }
                
                // Not in cache, download it
                InputStream in = new URL(secureUrl).openStream();
                
                // Set up options to downscale the image
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2; // Downscale by factor of 2
                
                // Decode the stream
                Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
                
                // Add to cache if download successful
                if (bitmap != null) {
                    addBitmapToCache(secureUrl, bitmap);
                }
                return bitmap;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap result) {
            // Check if ImageView still needs this image
            if (imageView.getTag() != null && imageView.getTag().equals(originalUrl)) {
                if (result != null) {
                    imageView.setImageBitmap(result);
                }
            }
        }
    }

    public static void loadImage(ImageView imageView, String url) {
        if (url != null && !url.isEmpty()) {
            // Check if the image is already in memory cache
            String secureUrl = url.replace("http://", "https://");
            Bitmap cachedBitmap = getBitmapFromCache(secureUrl);
            
            if (cachedBitmap != null) {
                // Use cached image
                imageView.setImageBitmap(cachedBitmap);
            } else {
                // Download image
                new LoadImageTask(imageView, url).execute(url);
            }
        }
    }
} 