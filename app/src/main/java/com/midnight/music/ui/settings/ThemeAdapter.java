package com.midnight.music.ui.settings;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.midnight.music.R;
import com.midnight.music.utils.ThemeManager;

import java.util.ArrayList;
import java.util.List;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder> {
    
    private final Context context;
    private final ThemeManager themeManager;
    private final List<ThemeItem> themes = new ArrayList<>();
    private String selectedTheme;
    private OnThemeClickListener listener;

    public interface OnThemeClickListener {
        void onThemeClick(String themeKey);
    }

    public ThemeAdapter(Context context, ThemeManager themeManager) {
        this.context = context;
        this.themeManager = themeManager;
        this.selectedTheme = themeManager.getSelectedTheme();
        initializeThemes();
    }

    public void setOnThemeClickListener(OnThemeClickListener listener) {
        this.listener = listener;
    }

    private void initializeThemes() {
        themes.add(new ThemeItem("midnight", "Midnight", 
            new int[]{Color.parseColor("#BB86FC"), Color.parseColor("#0F0F0F")}));
        themes.add(new ThemeItem("ocean", "Ocean", 
            new int[]{Color.parseColor("#64B5F6"), Color.parseColor("#0D1B2A")}));
        themes.add(new ThemeItem("sunset", "Sunset", 
            new int[]{Color.parseColor("#FF6D00"), Color.parseColor("#1A0F0A")}));
        themes.add(new ThemeItem("forest", "Forest", 
            new int[]{Color.parseColor("#69F0AE"), Color.parseColor("#0A1A0F")}));
        themes.add(new ThemeItem("light", "Light", 
            new int[]{Color.parseColor("#7A2BE2"), Color.parseColor("#FFFFFF")}));
        themes.add(new ThemeItem("amoled", "AMOLED", 
            new int[]{Color.parseColor("#BB86FC"), Color.parseColor("#000000")}));
    }

    public void setSelectedTheme(String theme) {
        this.selectedTheme = theme;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ThemeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_theme, parent, false);
        return new ThemeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ThemeViewHolder holder, int position) {
        ThemeItem theme = themes.get(position);
        holder.bind(theme, theme.key.equals(selectedTheme));
    }

    @Override
    public int getItemCount() {
        return themes.size();
    }

    class ThemeViewHolder extends RecyclerView.ViewHolder {
        private final LinearLayout container;
        private final View themeCircle;
        private final ImageView checkIcon;
        private final TextView themeName;

        ThemeViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.theme_item_container);
            themeCircle = itemView.findViewById(R.id.theme_circle);
            checkIcon = itemView.findViewById(R.id.check_icon);
            themeName = itemView.findViewById(R.id.theme_name);
        }

        void bind(ThemeItem theme, boolean isSelected) {
            themeName.setText(theme.name);
            
            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            float radiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 100, context.getResources().getDisplayMetrics());
            drawable.setGradientRadius(radiusPx);
            drawable.setColors(new int[]{theme.colors[0], theme.colors[1]});
            
            themeCircle.setBackground(drawable);

            checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            
            if (isSelected) {
                themeName.setTextColor(context.getColor(R.color.primary));
            } else {
                themeName.setTextColor(context.getColor(R.color.text_secondary));
            }

            container.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onThemeClick(theme.key);
                }
            });
        }
    }

    static class ThemeItem {
        String key;
        String name;
        int[] colors;

        ThemeItem(String key, String name, int[] colors) {
            this.key = key;
            this.name = name;
            this.colors = colors;
        }
    }
}
