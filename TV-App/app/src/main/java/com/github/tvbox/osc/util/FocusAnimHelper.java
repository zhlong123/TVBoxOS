package com.github.tvbox.osc.util;

import android.graphics.Outline;
import android.os.Build;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * TV 遥控器焦点动画：统一缩放与时长，避免各页面手感不一致。
 */
public final class FocusAnimHelper {
    private static final DecelerateInterpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final long DURATION_IN_MS = 220L;
    private static final long DURATION_OUT_MS = 180L;
    private static final float SCALE_FOCUSED = 1.08f;
    private static final float SCALE_SEARCH_WORD_FOCUSED = 1.0f;
    private static final float SCALE_NORMAL = 1.0f;
    private static final float FOCUS_TRANSLATION_Z = 48f;
    private static final float POSTER_FOCUS_ELEVATION = 36f;
    private static final float POSTER_FOCUS_LIFT_MM = 12f;
    private static final float SEARCH_WORD_TRANSLATION_Z = 28f;
    private static final float SEARCH_WORD_FOCUS_ELEVATION = 24f;
    private static final float SEARCH_WORD_FOCUS_LIFT_MM = 2f;

    private FocusAnimHelper() {
    }

    /**
     * 焦点在 item 内部子 View（如 mItemFrame）时，将 OK/Enter 传递到绑定了 Adapter 点击的 item 根布局。
     */
    public static boolean performItemClick(@Nullable View from) {
        if (from == null) {
            return false;
        }
        View current = from;
        while (current != null) {
            if (current.hasOnClickListeners() && current.isClickable()) {
                return current.performClick();
            }
            ViewParent parent = current.getParent();
            if (isRecyclerViewParent(parent)) {
                break;
            }
            if (parent instanceof View) {
                current = (View) parent;
            } else {
                break;
            }
        }
        return from.performClick();
    }

    private static boolean isRecyclerViewParent(@Nullable ViewParent parent) {
        if (parent == null) {
            return false;
        }
        if (parent instanceof RecyclerView) {
            return true;
        }
        return parent.getClass().getName().contains("TvRecyclerView");
    }

    private static boolean isConfirmKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private static void attachConfirmKeyListener(View focusTarget, View clickRoot) {
        focusTarget.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP || !isConfirmKey(keyCode)) {
                return false;
            }
            return performItemClick(clickRoot);
        });
    }

    public static void focusIn(View view) {
        if (view == null) {
            return;
        }
        view.animate()
                .scaleX(SCALE_FOCUSED)
                .scaleY(SCALE_FOCUSED)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
    }

    /** 海报卡片：仅放大海报框 + 琥珀描边，标题仅变色不缩放。 */
    public static void focusInPoster(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View focusTarget = resolvePosterFocusTarget(itemRoot);
        View scaleTarget = resolvePosterScaleTarget(itemRoot);
        disableDefaultFocusHighlight(focusTarget);
        UiLayoutConfig.ensureNoClipOnParents(itemRoot);
        itemRoot.setTranslationZ(FOCUS_TRANSLATION_Z);
        // 不可对 RecyclerView 的 item 调用 bringToFront，否则会打乱 ChildHelper 隐藏池导致崩溃
        if (itemRoot.getParent() instanceof ViewGroup) {
            ((ViewGroup) itemRoot.getParent()).invalidate();
        }
        applyItemPivot(scaleTarget);
        ensurePosterOutline(scaleTarget);
        setPosterFrameSelected(scaleTarget, true);
        applyPosterGlow(itemRoot, scaleTarget, true);
        float liftPx = AutoSizeUtils.mm2px(itemRoot.getContext(), POSTER_FOCUS_LIFT_MM);
        itemRoot.animate().cancel();
        itemRoot.animate()
                .translationY(-liftPx)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
        scaleTarget.animate().cancel();
        float scale = UiLayoutConfig.getFocusScale();
        scaleTarget.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
        stylePosterTitle(itemRoot, true);
        bringPosterFocusRingToFront(itemRoot);
        refreshPosterFocusRing(itemRoot);
        if (focusTarget != null) {
            focusTarget.refreshDrawableState();
        }
    }

    public static void focusOut(View view) {
        if (view == null) {
            return;
        }
        view.animate()
                .scaleX(SCALE_NORMAL)
                .scaleY(SCALE_NORMAL)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
    }

    public static void focusOutPoster(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View scaleTarget = resolvePosterScaleTarget(itemRoot);
        itemRoot.setTranslationZ(0f);
        applyItemPivot(scaleTarget);
        setPosterFrameSelected(scaleTarget, false);
        applyPosterGlow(itemRoot, scaleTarget, false);
        itemRoot.animate().cancel();
        itemRoot.animate()
                .translationY(0f)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
        scaleTarget.animate().cancel();
        scaleTarget.animate()
                .scaleX(SCALE_NORMAL)
                .scaleY(SCALE_NORMAL)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
        stylePosterTitle(itemRoot, false);
        refreshPosterFocusRing(itemRoot);
        View focusTarget = resolvePosterFocusTarget(itemRoot);
        if (focusTarget != null) {
            focusTarget.refreshDrawableState();
        }
    }

    /** 搜索热词 / 历史标签：三层金色光晕 + 轻微上浮，与海报卡片风格统一。 */
    public static void attachSearchWordFocus(View itemRoot) {
        View target = resolveSearchWordFocusTarget(itemRoot);
        if (target == null || Boolean.TRUE.equals(target.getTag(R.id.tag_search_word_focus_attached))) {
            return;
        }
        target.setTag(R.id.tag_search_word_focus_attached, Boolean.TRUE);
        disableDefaultFocusHighlight(target);
        attachConfirmKeyListener(target, itemRoot);
        target.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusInSearchWord(itemRoot);
            } else {
                focusOutSearchWord(itemRoot);
            }
        });
        if (target.isFocused()) {
            focusInSearchWord(itemRoot);
        } else {
            resetSearchWord(itemRoot);
        }
    }

    public static void onSearchWordItemPreSelected(@Nullable View itemRoot) {
        if (itemRoot != null) {
            focusOutSearchWord(itemRoot);
        }
    }

    public static void onSearchWordItemSelected(@Nullable View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View focusTarget = resolveSearchWordFocusTarget(itemRoot);
        if (focusTarget != null && !focusTarget.isFocused()) {
            focusTarget.requestFocus();
        }
        itemRoot.post(() -> focusInSearchWord(itemRoot));
    }

    public static void focusInSearchWord(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View frame = resolveSearchWordFrame(itemRoot);
        View focusTarget = resolveSearchWordFocusTarget(itemRoot);
        disableDefaultFocusHighlight(focusTarget);
        applySearchWordPivot(frame);
        setSearchWordFrameSelected(frame, true);
        applySearchWordGlow(itemRoot, frame, true);
        frame.animate().cancel();
        frame.animate()
                .scaleX(SCALE_SEARCH_WORD_FOCUSED)
                .scaleY(SCALE_SEARCH_WORD_FOCUSED)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
        styleSearchWordText(itemRoot, true);
        styleSearchWordRank(itemRoot, true);
        refreshSearchWordFocusRing(itemRoot);
        if (focusTarget != null) {
            focusTarget.refreshDrawableState();
        }
    }

    public static void focusOutSearchWord(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View frame = resolveSearchWordFrame(itemRoot);
        applySearchWordPivot(frame);
        setSearchWordFrameSelected(frame, false);
        applySearchWordGlow(itemRoot, frame, false);
        frame.animate().cancel();
        frame.animate()
                .scaleX(SCALE_NORMAL)
                .scaleY(SCALE_NORMAL)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
        styleSearchWordText(itemRoot, false);
        styleSearchWordRank(itemRoot, false);
        refreshSearchWordFocusRing(itemRoot);
        View focusTarget = resolveSearchWordFocusTarget(itemRoot);
        if (focusTarget != null) {
            focusTarget.refreshDrawableState();
        }
    }

    public static void resetSearchWord(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View frame = resolveSearchWordFrame(itemRoot);
        frame.animate().cancel();
        frame.setScaleX(SCALE_NORMAL);
        frame.setScaleY(SCALE_NORMAL);
        setSearchWordFrameSelected(frame, false);
        applySearchWordGlow(itemRoot, frame, false);
        styleSearchWordText(itemRoot, false);
        styleSearchWordRank(itemRoot, false);
    }

    private static void styleSearchWordRank(View itemRoot, boolean focused) {
        TextView rank = itemRoot.findViewById(R.id.tvSearchWordRank);
        if (rank == null) {
            return;
        }
        if (focused) {
            rank.setTextColor(itemRoot.getContext().getColor(R.color.ui_focus_ring_bright));
        } else {
            int pos = -1;
            try {
                pos = Integer.parseInt(rank.getText().toString()) - 1;
            } catch (NumberFormatException ignored) {
            }
            int rankColorRes;
            if (pos == 0) {
                rankColorRes = R.color.search_rank_1;
            } else if (pos == 1) {
                rankColorRes = R.color.search_rank_2;
            } else if (pos == 2) {
                rankColorRes = R.color.search_rank_3;
            } else {
                rankColorRes = R.color.ui_text_muted;
            }
            rank.setTextColor(itemRoot.getContext().getColor(rankColorRes));
        }
        rank.invalidate();
    }

    private static void applySearchWordPivot(View view) {
        Runnable apply = () -> {
            if (view.getWidth() > 0 && view.getHeight() > 0) {
                view.setPivotX(view.getWidth() * 0.5f);
                view.setPivotY(view.getHeight() * 0.5f);
            }
        };
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            apply.run();
        } else {
            view.post(apply);
        }
    }

    /** 顶栏文字按钮：仅用 background selector 描边，不做整控件缩放。 */
    public static void attachToolbarButtonFocus(View view) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.tag_toolbar_focus_attached))) {
            return;
        }
        view.setTag(R.id.tag_toolbar_focus_attached, Boolean.TRUE);
        disableDefaultFocusHighlight(view);
        resetToolbarButton(view);
    }

    public static void resetToolbarButton(@Nullable View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setScaleX(SCALE_NORMAL);
        view.setScaleY(SCALE_NORMAL);
        view.setTranslationZ(0f);
        view.refreshDrawableState();
    }

    public static void blurToolbarButton(@Nullable View view) {
        resetToolbarButton(view);
        if (view != null) {
            view.clearFocus();
        }
    }

    /** 绑定到 item 根布局，任意方式获焦（遥控 / 云遥控）都会触发选中效果。 */
    public static void attachPosterItemFocus(View itemRoot) {
        View target = resolvePosterFocusTarget(itemRoot);
        if (target == null || Boolean.TRUE.equals(target.getTag(R.id.tag_poster_focus_attached))) {
            return;
        }
        target.setTag(R.id.tag_poster_focus_attached, Boolean.TRUE);
        disableDefaultFocusHighlight(target);
        attachConfirmKeyListener(target, itemRoot);
        target.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusInPoster(itemRoot);
            } else {
                focusOutPoster(itemRoot);
            }
        });
        if (target.isFocused()) {
            focusInPoster(itemRoot);
        } else {
            resetPosterItem(itemRoot);
        }
    }

    /** TvRecyclerView 切换选中项时调用，避免仅有“选中”而无 View 焦点时动画不触发。 */
    public static void onPosterItemPreSelected(@Nullable View itemRoot) {
        if (itemRoot != null) {
            focusOutPoster(itemRoot);
        }
    }

    public static void onPosterItemSelected(@Nullable View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View focusTarget = resolvePosterFocusTarget(itemRoot);
        if (focusTarget != null && !focusTarget.isFocused()) {
            focusTarget.requestFocus();
        }
        // 始终刷新光晕/缩放，避免 TvRecyclerView 仅切换选中态而不触发焦点回调
        itemRoot.post(() -> focusInPoster(itemRoot));
    }

    public static void resetPosterItem(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View scaleTarget = resolvePosterScaleTarget(itemRoot);
        scaleTarget.animate().cancel();
        scaleTarget.setScaleX(SCALE_NORMAL);
        scaleTarget.setScaleY(SCALE_NORMAL);
        setPosterFrameSelected(scaleTarget, false);
        itemRoot.setTranslationY(0f);
        applyPosterGlow(itemRoot, scaleTarget, false);
        itemRoot.setTranslationZ(0f);
        stylePosterTitle(itemRoot, false);
    }

    private static View resolvePosterScaleTarget(View itemRoot) {
        View frame = itemRoot.findViewById(R.id.mItemFrame);
        return frame != null ? frame : itemRoot;
    }

    private static View resolvePosterFocusTarget(View itemRoot) {
        if (itemRoot == null) {
            return null;
        }
        View frame = itemRoot.findViewById(R.id.mItemFrame);
        if (frame != null && frame.isFocusable()) {
            return frame;
        }
        return itemRoot;
    }

    private static void stylePosterTitle(View itemRoot, boolean focused) {
        TextView title = itemRoot.findViewById(R.id.tvName);
        if (title == null) {
            return;
        }
        title.getPaint().setFakeBoldText(focused);
        title.setSelected(focused);
        title.setTextColor(itemRoot.getContext().getColor(
                focused ? R.color.ui_focus_ring_bright : R.color.ui_text_primary));
        title.invalidate();
    }

    private static void bringPosterFocusRingToFront(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View ring = itemRoot.findViewById(R.id.posterFocusRing);
        if (ring != null) {
            ring.bringToFront();
            if (ring.getParent() instanceof ViewGroup) {
                ((ViewGroup) ring.getParent()).invalidate();
            }
        }
    }

    private static void styleSearchWordText(View itemRoot, boolean focused) {
        TextView text = resolveSearchWordText(itemRoot);
        if (text == null) {
            return;
        }
        text.getPaint().setFakeBoldText(focused);
        text.setSelected(focused);
        text.setTextColor(itemRoot.getContext().getColor(
                focused ? R.color.ui_focus_ring_bright : R.color.ui_text_primary));
        text.invalidate();
    }

    @Nullable
    private static View resolveSearchWordFrame(View itemRoot) {
        if (itemRoot == null) {
            return null;
        }
        View frame = itemRoot.findViewById(R.id.searchWordFrame);
        return frame != null ? frame : itemRoot;
    }

    @Nullable
    private static View resolveSearchWordFocusTarget(View itemRoot) {
        if (itemRoot == null) {
            return null;
        }
        View frame = itemRoot.findViewById(R.id.searchWordFrame);
        if (frame != null && frame.isFocusable()) {
            return frame;
        }
        if (itemRoot instanceof TextView) {
            return itemRoot;
        }
        return itemRoot;
    }

    private static void setSearchWordFrameSelected(@Nullable View frame, boolean selected) {
        if (frame != null) {
            frame.setSelected(selected);
            frame.refreshDrawableState();
        }
    }

    private static void refreshSearchWordFocusRing(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View ring = itemRoot.findViewById(R.id.searchWordFocusRing);
        if (ring != null) {
            ring.refreshDrawableState();
            ring.invalidate();
        }
    }

    private static void applySearchWordGlow(View itemRoot, View frame, boolean focused) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        if (frame != null) {
            frame.setElevation(focused ? SEARCH_WORD_FOCUS_ELEVATION : 0f);
        }
        if (itemRoot != null) {
            itemRoot.setElevation(focused ? SEARCH_WORD_FOCUS_ELEVATION * 0.5f : 0f);
        }
    }

    @Nullable
    private static TextView resolveSearchWordText(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }
        return view.findViewById(R.id.tvSearchWord);
    }

    private static void applyItemPivot(View view) {
        Runnable apply = () -> {
            if (view.getWidth() > 0 && view.getHeight() > 0) {
                view.setPivotX(view.getWidth() * 0.5f);
                // 以底边为轴心上扩，避免 115% 缩放 + 光晕压住下方标题
                view.setPivotY(view.getHeight());
            }
        };
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            apply.run();
        } else {
            view.post(apply);
        }
    }

    private static void disableDefaultFocusHighlight(@Nullable View view) {
        if (view == null) {
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
    }

    private static void ensurePosterOutline(View scaleTarget) {
        if (scaleTarget == null
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || Boolean.TRUE.equals(scaleTarget.getTag(R.id.tag_poster_outline_set))) {
            return;
        }
        scaleTarget.setTag(R.id.tag_poster_outline_set, Boolean.TRUE);
        final float radius = scaleTarget.getResources().getDimension(R.dimen.radius_poster_card);
        scaleTarget.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    private static void refreshPosterFocusRing(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View ring = itemRoot.findViewById(R.id.posterFocusRing);
        if (ring != null) {
            ring.refreshDrawableState();
            ring.invalidate();
        }
    }

    private static void setPosterFrameSelected(@Nullable View scaleTarget, boolean selected) {
        if (scaleTarget != null) {
            scaleTarget.setSelected(selected);
            scaleTarget.refreshDrawableState();
        }
    }

    private static void applyPosterGlow(View itemRoot, View scaleTarget, boolean focused) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return;
        }
        if (scaleTarget != null) {
            scaleTarget.setElevation(focused ? POSTER_FOCUS_ELEVATION : 0f);
            scaleTarget.setClipToOutline(false);
        }
        if (itemRoot != null) {
            itemRoot.setElevation(focused ? POSTER_FOCUS_ELEVATION * 0.6f : 0f);
        }
    }
}
