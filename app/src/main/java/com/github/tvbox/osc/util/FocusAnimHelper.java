package com.github.tvbox.osc.util;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;

/**
 * TV 遥控器焦点动画：统一缩放与时长，避免各页面手感不一致。
 */
public final class FocusAnimHelper {
    private static final DecelerateInterpolator INTERPOLATOR = new DecelerateInterpolator();
    private static final long DURATION_IN_MS = 220L;
    private static final long DURATION_OUT_MS = 180L;
    private static final float SCALE_FOCUSED = 1.08f;
    private static final float SCALE_POSTER_FOCUSED = 1.12f;
    private static final float SCALE_SEARCH_WORD_FOCUSED = 1.05f;
    private static final float SCALE_NORMAL = 1.0f;
    private static final float FOCUS_TRANSLATION_Z = 36f;
    private static final float SEARCH_WORD_TRANSLATION_Z = 20f;

    private FocusAnimHelper() {
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

    /** 海报卡片：只放大海报区域，整项抬高，避免与相邻卡片挤在一起。 */
    public static void focusInPoster(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View card = getPosterCard(itemRoot);
        itemRoot.setTranslationZ(FOCUS_TRANSLATION_Z);
        itemRoot.bringToFront();
        if (itemRoot.getParent() instanceof ViewGroup) {
            ((ViewGroup) itemRoot.getParent()).invalidate();
        }
        applyPosterPivot(card);
        card.animate().cancel();
        float scale = UiLayoutConfig.getFocusScale();
        card.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
        stylePosterTitle(itemRoot, true);
        View focusTarget = resolvePosterFocusTarget(itemRoot);
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
        View card = getPosterCard(itemRoot);
        itemRoot.setTranslationZ(0f);
        applyPosterPivot(card);
        card.animate().cancel();
        card.animate()
                .scaleX(SCALE_NORMAL)
                .scaleY(SCALE_NORMAL)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
        stylePosterTitle(itemRoot, false);
        View focusTarget = resolvePosterFocusTarget(itemRoot);
        if (focusTarget != null) {
            focusTarget.refreshDrawableState();
        }
    }

    /** 搜索热词 / 历史标签：琥珀描边 + 文字高亮，轻微放大，避免与整项 1.06 缩放叠在一起。 */
    public static void attachSearchWordFocus(View view) {
        if (view == null || Boolean.TRUE.equals(view.getTag(R.id.tag_search_word_focus_attached))) {
            return;
        }
        view.setTag(R.id.tag_search_word_focus_attached, Boolean.TRUE);
        view.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                focusInSearchWord(v);
            } else {
                focusOutSearchWord(v);
            }
        });
        if (view.isFocused()) {
            focusInSearchWord(view);
        } else {
            resetSearchWord(view);
        }
    }

    public static void focusInSearchWord(View view) {
        if (view == null) {
            return;
        }
        styleSearchWordText(view, true);
        view.setTranslationZ(SEARCH_WORD_TRANSLATION_Z);
        view.bringToFront();
        if (view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).invalidate();
        }
        view.animate().cancel();
        view.animate()
                .scaleX(SCALE_SEARCH_WORD_FOCUSED)
                .scaleY(SCALE_SEARCH_WORD_FOCUSED)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_IN_MS)
                .start();
    }

    public static void focusOutSearchWord(View view) {
        if (view == null) {
            return;
        }
        styleSearchWordText(view, false);
        view.setTranslationZ(0f);
        view.animate().cancel();
        view.animate()
                .scaleX(SCALE_NORMAL)
                .scaleY(SCALE_NORMAL)
                .setInterpolator(INTERPOLATOR)
                .setDuration(DURATION_OUT_MS)
                .start();
    }

    public static void resetSearchWord(View view) {
        if (view == null) {
            return;
        }
        view.animate().cancel();
        view.setScaleX(SCALE_NORMAL);
        view.setScaleY(SCALE_NORMAL);
        view.setTranslationZ(0f);
        styleSearchWordText(view, false);
    }

    /** 绑定到 item 根布局，任意方式获焦（遥控 / 云遥控）都会触发选中效果。 */
    public static void attachPosterItemFocus(View itemRoot) {
        View target = resolvePosterFocusTarget(itemRoot);
        if (target == null || Boolean.TRUE.equals(target.getTag(R.id.tag_poster_focus_attached))) {
            return;
        }
        target.setTag(R.id.tag_poster_focus_attached, Boolean.TRUE);
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

    public static void resetPosterItem(View itemRoot) {
        if (itemRoot == null) {
            return;
        }
        View card = getPosterCard(itemRoot);
        card.animate().cancel();
        card.setScaleX(SCALE_NORMAL);
        card.setScaleY(SCALE_NORMAL);
        itemRoot.setTranslationZ(0f);
        stylePosterTitle(itemRoot, false);
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

    private static View getPosterCard(View itemRoot) {
        View frame = itemRoot.findViewById(R.id.mItemFrame);
        if (frame != null) {
            return frame;
        }
        if (itemRoot instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) itemRoot;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child instanceof FrameLayout) {
                    return child;
                }
            }
        }
        return itemRoot;
    }

    private static void stylePosterTitle(View itemRoot, boolean focused) {
        TextView title = itemRoot.findViewById(R.id.tvName);
        if (title == null) {
            return;
        }
        title.getPaint().setFakeBoldText(focused);
        title.setTextColor(itemRoot.getContext().getColor(
                focused ? R.color.ui_accent_bright : R.color.ui_text_primary));
        title.invalidate();
    }

    private static void styleSearchWordText(View view, boolean focused) {
        TextView text = resolveSearchWordText(view);
        if (text == null) {
            return;
        }
        text.getPaint().setFakeBoldText(focused);
        text.setTextColor(view.getContext().getColor(
                focused ? R.color.ui_accent_bright : R.color.ui_text_primary));
        text.invalidate();
    }

    @Nullable
    private static TextView resolveSearchWordText(View view) {
        if (view instanceof TextView) {
            return (TextView) view;
        }
        return view.findViewById(R.id.tvSearchWord);
    }

    private static void applyPosterPivot(View view) {
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            view.setPivotX(view.getWidth() * 0.5f);
            view.setPivotY(view.getHeight() * 0.88f);
        } else {
            view.post(() -> {
                if (view.getWidth() > 0 && view.getHeight() > 0) {
                    view.setPivotX(view.getWidth() * 0.5f);
                    view.setPivotY(view.getHeight() * 0.88f);
                }
            });
        }
    }
}
