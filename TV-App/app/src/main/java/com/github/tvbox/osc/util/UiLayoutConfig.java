package com.github.tvbox.osc.util;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.github.tvbox.osc.R;

import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import androidx.viewpager.widget.ViewPager;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * TV 界面布局参数，支持 Hawk 持久化与云后端 settings 下发。
 */
public final class UiLayoutConfig {

    public static final int DEFAULT_CARD_WIDTH_MM = 160;
    public static final int DEFAULT_CARD_HEIGHT_MM = 210;
    public static final int DEFAULT_SEARCH_CARD_WIDTH_MM = 145;
    public static final int DEFAULT_SEARCH_CARD_HEIGHT_MM = 193;
    public static final int DEFAULT_GRID_SPAN = 7;
    public static final int DEFAULT_GRID_SPACING_MM = 48;
    public static final int DEFAULT_ITEM_MARGIN_MM = 24;
    public static final int DEFAULT_FOCUS_SCALE_PERCENT = 115;

    private UiLayoutConfig() {
    }

    public static void load() {
        // Hawk 已在 getXxx 时读取，此处预留启动时预热
    }

    public static int getCardWidthMm() {
        return clamp(Hawk.get(HawkConfig.UI_CARD_WIDTH, DEFAULT_CARD_WIDTH_MM), 120, 260);
    }

    public static int getCardHeightMm() {
        return clamp(Hawk.get(HawkConfig.UI_CARD_HEIGHT, DEFAULT_CARD_HEIGHT_MM), 150, 340);
    }

    public static int getSearchCardWidthMm() {
        return clamp(Hawk.get(HawkConfig.UI_SEARCH_CARD_WIDTH, DEFAULT_SEARCH_CARD_WIDTH_MM), 110, 220);
    }

    public static int getSearchCardHeightMm() {
        return clamp(Hawk.get(HawkConfig.UI_SEARCH_CARD_HEIGHT, DEFAULT_SEARCH_CARD_HEIGHT_MM), 140, 290);
    }

    public static int getGridSpan(boolean baseOnWidth, int fallback) {
        int configured = Hawk.get(HawkConfig.UI_GRID_SPAN, 0);
        if (configured > 0) {
            return clamp(configured, 3, 10);
        }
        return fallback;
    }

    public static int getGridSpacingMm() {
        return clamp(Hawk.get(HawkConfig.UI_GRID_SPACING, DEFAULT_GRID_SPACING_MM), 16, 64);
    }

    public static int getItemMarginMm() {
        return clamp(Hawk.get(HawkConfig.UI_ITEM_MARGIN, DEFAULT_ITEM_MARGIN_MM), 8, 32);
    }

    public static float getFocusScale() {
        int percent = clamp(Hawk.get(HawkConfig.UI_FOCUS_SCALE, DEFAULT_FOCUS_SCALE_PERCENT), 100, 130);
        return percent / 100f;
    }

    public static boolean apply(String key, String value) {
        if (key == null) {
            return false;
        }
        switch (key) {
            case HawkConfig.UI_CARD_WIDTH:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_CARD_WIDTH_MM), 120, 260));
                return true;
            case HawkConfig.UI_CARD_HEIGHT:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_CARD_HEIGHT_MM), 150, 340));
                return true;
            case HawkConfig.UI_SEARCH_CARD_WIDTH:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_SEARCH_CARD_WIDTH_MM), 110, 220));
                return true;
            case HawkConfig.UI_SEARCH_CARD_HEIGHT:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_SEARCH_CARD_HEIGHT_MM), 140, 290));
                return true;
            case HawkConfig.UI_GRID_SPAN:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_GRID_SPAN), 0, 10));
                return true;
            case HawkConfig.UI_GRID_SPACING:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_GRID_SPACING_MM), 16, 64));
                return true;
            case HawkConfig.UI_ITEM_MARGIN:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_ITEM_MARGIN_MM), 8, 32));
                return true;
            case HawkConfig.UI_FOCUS_SCALE:
                Hawk.put(key, clamp(parseInt(value, DEFAULT_FOCUS_SCALE_PERCENT), 100, 130));
                return true;
            default:
                return false;
        }
    }

    public static int getGridSpacingPx(Context context) {
        return AutoSizeUtils.mm2px(context, getGridSpacingMm());
    }

    /** 统一网格间距与裁切，避免各页面硬编码 10px / vs_15 导致卡片挤在一起。 */
    public static void applyGridRecyclerView(TvRecyclerView gridView) {
        if (gridView == null) {
            return;
        }
        int spacingPx = getGridSpacingPx(gridView.getContext());
        gridView.setSpacingWithMargins(spacingPx, spacingPx);
        gridView.setClipChildren(false);
        gridView.setClipToPadding(false);
        int topReservePx = gridView.getResources().getDimensionPixelSize(R.dimen.grid_focus_top_padding);
        if (gridView.getPaddingTop() < topReservePx) {
            gridView.setPadding(
                    gridView.getPaddingLeft(),
                    topReservePx,
                    gridView.getPaddingRight(),
                    gridView.getPaddingBottom());
        }
        ensureNoClipOnParents(gridView);
        gridView.requestLayout();
    }

    /** 焦点放大/上浮时，向上解除父容器裁切；在 ViewPager 处停止，避免相邻页卡片叠到当前分类页。 */
    public static void ensureNoClipOnParents(View view) {
        if (view == null) {
            return;
        }
        View current = view;
        while (current instanceof ViewGroup) {
            if (current instanceof ViewPager) {
                break;
            }
            ViewGroup group = (ViewGroup) current;
            if (group.getClipChildren()) {
                group.setClipChildren(false);
            }
            if (group.getClipToPadding()) {
                group.setClipToPadding(false);
            }
            if (!(group.getParent() instanceof View)) {
                break;
            }
            current = (View) group.getParent();
        }
    }

    public static void applyPosterItemSize(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View card = itemRoot.findViewById(R.id.mItemFrame);
        if (card == null) {
            return;
        }
        int w = AutoSizeUtils.mm2px(itemRoot.getContext(), getCardWidthMm());
        int h = AutoSizeUtils.mm2px(itemRoot.getContext(), getCardHeightMm());
        int margin = AutoSizeUtils.mm2px(itemRoot.getContext(), getItemMarginMm());
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp != null) {
            lp.width = w;
            lp.height = h;
            card.setLayoutParams(lp);
        }
        ViewGroup.LayoutParams rootLp = itemRoot.getLayoutParams();
        if (rootLp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) rootLp;
            mlp.setMargins(margin, margin, margin, margin);
            itemRoot.setLayoutParams(mlp);
        }
        TextView title = itemRoot.findViewById(R.id.tvName);
        if (title != null) {
            ViewGroup.LayoutParams titleLp = title.getLayoutParams();
            if (titleLp != null) {
                titleLp.width = w;
                title.setLayoutParams(titleLp);
            }
        }
        TextView actor = itemRoot.findViewById(R.id.tvActor);
        if (actor != null) {
            ViewGroup.LayoutParams actorLp = actor.getLayoutParams();
            if (actorLp != null) {
                actorLp.width = w;
                actor.setLayoutParams(actorLp);
            }
        }
    }

    public static void applySearchItemSize(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View card = itemRoot.findViewById(R.id.mItemFrame);
        if (card == null) {
            card = itemRoot;
        }
        int w = AutoSizeUtils.mm2px(itemRoot.getContext(), getSearchCardWidthMm());
        int h = AutoSizeUtils.mm2px(itemRoot.getContext(), getSearchCardHeightMm());
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        if (lp != null) {
            lp.width = w;
            lp.height = h;
            card.setLayoutParams(lp);
        }
    }

    private static int parseInt(String value, int def) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
