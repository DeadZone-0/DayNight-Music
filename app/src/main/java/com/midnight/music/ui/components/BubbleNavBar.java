package com.midnight.music.ui.components;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.transition.ChangeBounds;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;
import androidx.transition.TransitionSet;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.midnight.music.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Signature Animeze Bubble Floating Navigation Bar.
 * Translucent dark obsidian glass container (62dp height, 32dp radius, 1dp subtle border, 16dp elevation).
 * Frosted active bubble pill that expands smoothly to reveal icon + bold label with 1dp border.
 */
public class BubbleNavBar extends FrameLayout {

    public interface OnTabSelectedListener {
        void onTabSelected(int itemId);
    }

    private static class TabItem {
        final int id;
        final String label;
        final int iconRes;
        FrameLayout slot;
        View pillContainer;
        ImageView iconView;
        TextView labelView;

        TabItem(int id, String label, int iconRes) {
            this.id = id;
            this.label = label;
            this.iconRes = iconRes;
        }
    }

    private final List<TabItem> tabs = new ArrayList<>();
    private LinearLayout rowLayout;
    private int selectedId = -1;
    private OnTabSelectedListener listener;
    private NavController navController;
    private int accentColor = Color.WHITE;

    public BubbleNavBar(@NonNull Context context) {
        this(context, null);
    }

    public BubbleNavBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BubbleNavBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setBackgroundResource(R.drawable.bg_nav_bar_animeze);
        setElevation(dpToPx(20));
        setOutlineProvider(android.view.ViewOutlineProvider.BACKGROUND);
        setClipToOutline(true);

        rowLayout = new LinearLayout(context);
        rowLayout.setOrientation(LinearLayout.HORIZONTAL);
        rowLayout.setGravity(Gravity.CENTER_VERTICAL);
        rowLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        int padH = dpToPx(8);
        int padV = dpToPx(6);
        rowLayout.setPadding(padH, padV, padH, padV);
        addView(rowLayout);

        // Animeze-style 4 Core Navigation Tabs
        tabs.add(new TabItem(R.id.navigation_home, "Home", R.drawable.ic_home));
        tabs.add(new TabItem(R.id.navigation_search, "Search", R.drawable.ic_search));
        tabs.add(new TabItem(R.id.navigation_library, "Library", R.drawable.ic_library));
        tabs.add(new TabItem(R.id.navigation_profile, "Profile", R.drawable.ic_profile));

        LayoutInflater inflater = LayoutInflater.from(context);

        for (TabItem tab : tabs) {
            FrameLayout slot = new FrameLayout(context);
            LinearLayout.LayoutParams slotLp = new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f);
            slot.setLayoutParams(slotLp);
            tab.slot = slot;

            View pill = inflater.inflate(R.layout.layout_bubble_nav_item, slot, false);
            FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dpToPx(44));
            pillLp.gravity = Gravity.CENTER;
            pill.setLayoutParams(pillLp);

            tab.pillContainer = pill;
            tab.iconView = pill.findViewById(R.id.nav_item_icon);
            tab.labelView = pill.findViewById(R.id.nav_item_label);

            tab.iconView.setImageResource(tab.iconRes);
            tab.labelView.setText(tab.label);

            slot.setOnClickListener(v -> selectTab(tab.id, true));
            pill.setOnClickListener(v -> selectTab(tab.id, true));

            slot.addView(pill);
            rowLayout.addView(slot);
        }

        setSelectedItemId(R.id.navigation_home, false);
    }

    public void setOnTabSelectedListener(OnTabSelectedListener listener) {
        this.listener = listener;
    }

    public void setAccentColor(int color) {
        this.accentColor = color;
        refreshTabStates(false);
    }

    public void setSelectedItemId(int id) {
        setSelectedItemId(id, true);
    }

    public void setSelectedItemId(int id, boolean animate) {
        if (selectedId == id) return;
        selectedId = id;
        refreshTabStates(animate);
    }

    public int getSelectedItemId() {
        return selectedId;
    }

    private void selectTab(int id, boolean userInitiated) {
        if (selectedId == id) {
            return;
        }
        selectedId = id;
        refreshTabStates(true);

        if (userInitiated) {
            if (listener != null) {
                listener.onTabSelected(id);
            }
            if (navController != null) {
                post(() -> {
                    try {
                        int startDest = navController.getGraph().getStartDestinationId();
                        NavOptions navOptions = new NavOptions.Builder()
                                .setLaunchSingleTop(true)
                                .setRestoreState(true)
                                .setPopUpTo(startDest, false, true)
                                .build();
                        navController.navigate(id, null, navOptions);
                    } catch (Exception e) {
                        try {
                            navController.navigate(id);
                        } catch (Exception ignored) {}
                    }
                });
            }
        }
    }

    private void refreshTabStates(boolean animate) {
        if (animate && rowLayout != null) {
            TransitionSet transition = new TransitionSet();
            transition.setOrdering(TransitionSet.ORDERING_TOGETHER);
            transition.addTransition(new ChangeBounds());
            transition.addTransition(new Fade());
            transition.setDuration(160);
            transition.setInterpolator(new FastOutSlowInInterpolator());
            TransitionManager.beginDelayedTransition(rowLayout, transition);
        }

        int inactiveColor = ContextCompat.getColor(getContext(), R.color.nav_icon_inactive);
        int activeColor = (accentColor != 0) ? accentColor : Color.WHITE;

        for (TabItem tab : tabs) {
            boolean isActive = (tab.id == selectedId);
            if (tab.slot != null) {
                LinearLayout.LayoutParams slotLp = (LinearLayout.LayoutParams) tab.slot.getLayoutParams();
                slotLp.weight = isActive ? 1.6f : 1.0f;
                tab.slot.setLayoutParams(slotLp);
            }
            if (isActive) {
                tab.pillContainer.setBackgroundResource(R.drawable.bg_nav_bubble_active);
                tab.iconView.setImageTintList(ColorStateList.valueOf(activeColor));
                tab.labelView.setTextColor(activeColor);
                tab.labelView.setVisibility(View.VISIBLE);
                tab.pillContainer.setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4));
            } else {
                tab.pillContainer.setBackgroundColor(Color.TRANSPARENT);
                tab.iconView.setImageTintList(ColorStateList.valueOf(inactiveColor));
                tab.labelView.setVisibility(View.GONE);
                tab.pillContainer.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
            }
        }
    }

    public void setupWithNavController(@NonNull NavController navController) {
        this.navController = navController;
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int destId = destination.getId();
            for (TabItem tab : tabs) {
                if (tab.id == destId) {
                    if (selectedId != destId) {
                        selectedId = destId;
                        refreshTabStates(true);
                    }
                    break;
                }
            }
        });
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
