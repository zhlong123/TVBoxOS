package com.github.tvbox.osc.server;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.dialog.BaseDialog;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.FocusAnimHelper;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 将手机遥控器指令映射为 TV 端 KeyEvent 并派发到当前 Activity。
 */
public class RemoteKeyHelper {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, Integer> KEY_MAP = new HashMap<>();

    static {
        KEY_MAP.put("up", KeyEvent.KEYCODE_DPAD_UP);
        KEY_MAP.put("down", KeyEvent.KEYCODE_DPAD_DOWN);
        KEY_MAP.put("left", KeyEvent.KEYCODE_DPAD_LEFT);
        KEY_MAP.put("right", KeyEvent.KEYCODE_DPAD_RIGHT);
        KEY_MAP.put("ok", KeyEvent.KEYCODE_DPAD_CENTER);
        KEY_MAP.put("center", KeyEvent.KEYCODE_DPAD_CENTER);
        KEY_MAP.put("enter", KeyEvent.KEYCODE_ENTER);
        KEY_MAP.put("back", KeyEvent.KEYCODE_BACK);
        KEY_MAP.put("menu", KeyEvent.KEYCODE_MENU);
        KEY_MAP.put("play", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        KEY_MAP.put("pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        KEY_MAP.put("play_pause", KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
    }

    public static void dispatch(String key) {
        if (key == null || key.trim().isEmpty()) {
            return;
        }
        String normalized = key.trim().toLowerCase(Locale.US);
        if ("home".equals(normalized)) {
            goHome();
            return;
        }
        Integer keyCode = KEY_MAP.get(normalized);
        if (keyCode == null) {
            try {
                keyCode = Integer.parseInt(normalized);
            } catch (NumberFormatException ignored) {
                return;
            }
        }
        dispatchKeyCode(keyCode);
    }

    private static void goHome() {
        MAIN.post(() -> {
            Activity activity = AppManager.getInstance().currentActivity();
            if (activity == null) {
                return;
            }
            Intent intent = new Intent(activity, HomeActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        });
    }

    private static void dispatchKeyCode(int keyCode) {
        MAIN.post(() -> {
            Activity activity = AppManager.getInstance().currentActivity();
            if (activity == null) {
                return;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                activity.onBackPressed();
                return;
            }

            View root = getDispatchRoot(activity);
            View focused = root.findFocus();
            if (focused == null) {
                focused = ensureFocusTarget(root);
            }
            if (focused == null) {
                return;
            }

            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                FocusAnimHelper.performItemClick(focused);
                return;
            }

            View before = root.findFocus();
            if (before == null) {
                before = focused;
            }

            if (dispatchRecyclerViewKey(before, keyCode)) {
                return;
            }

            if (dispatchSyntheticKey(root, activity, keyCode, before)) {
                return;
            }

            View current = root.findFocus();
            if (current == null || shouldSkipFocusTarget(current, before)) {
                current = before;
            }
            moveFocusByDirection(current, keyCodeToDirection(keyCode));
        });
    }

    private static View getDispatchRoot(Activity activity) {
        View dialogDecor = BaseDialog.getTopShowingDecorView();
        if (dialogDecor != null) {
            return dialogDecor;
        }
        return activity.getWindow().getDecorView();
    }

    /** 优先把方向键交给 TvRecyclerView（热门词竖列表 / 海报网格）。 */
    private static boolean dispatchRecyclerViewKey(View focused, int keyCode) {
        TvRecyclerView rv = findAncestor(focused, TvRecyclerView.class);
        if (rv == null) {
            return false;
        }
        View before = rv.findFocus();
        if (before == null) {
            before = focused;
        }

        long downTime = System.currentTimeMillis();
        KeyEvent down = createDpadEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent up = createDpadEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode);

        if (rv.dispatchKeyEvent(down)) {
            View after = rv.findFocus();
            if (isValidFocusMove(before, after)) {
                rv.dispatchKeyEvent(up);
                return true;
            }
        }

        int direction = keyCodeToDirection(keyCode);
        View next = before != null ? before.focusSearch(direction) : null;
        if (isValidFocusMove(before, next) && next.requestFocus()) {
            scrollRecyclerToFocused(rv, next);
            return true;
        }
        return false;
    }

    private static void scrollRecyclerToFocused(TvRecyclerView rv, View focused) {
        if (rv == null || focused == null) {
            return;
        }
        View item = focused;
        while (item != null && item.getParent() != rv) {
            if (item.getParent() instanceof View) {
                item = (View) item.getParent();
            } else {
                break;
            }
        }
        if (item != null && item.getParent() == rv) {
            int pos = rv.getChildAdapterPosition(item);
            if (pos >= 0) {
                rv.smoothScrollToPosition(pos);
                rv.setSelectedPosition(pos);
            }
        }
    }

    private static boolean dispatchSyntheticKey(View root, Activity activity, int keyCode, View before) {
        long downTime = System.currentTimeMillis();
        KeyEvent down = createDpadEvent(downTime, downTime, KeyEvent.ACTION_DOWN, keyCode);
        KeyEvent up = createDpadEvent(downTime, downTime + 50, KeyEvent.ACTION_UP, keyCode);

        root.dispatchKeyEvent(down);
        View afterDown = root.findFocus();
        if (isValidFocusMove(before, afterDown)) {
            root.dispatchKeyEvent(up);
            return true;
        }
        restoreFocusIfTrapped(before, afterDown);

        if (root == activity.getWindow().getDecorView()) {
            activity.dispatchKeyEvent(down);
            afterDown = root.findFocus();
            if (isValidFocusMove(before, afterDown)) {
                activity.dispatchKeyEvent(up);
                return true;
            }
            restoreFocusIfTrapped(before, afterDown);
        }

        root.dispatchKeyEvent(up);
        if (root == activity.getWindow().getDecorView()) {
            activity.dispatchKeyEvent(up);
        }
        View afterUp = root.findFocus();
        return isValidFocusMove(before, afterUp);
    }

    private static void restoreFocusIfTrapped(View before, View after) {
        if (before == null) {
            return;
        }
        if (after != null && after != before && shouldSkipFocusTarget(after, before)) {
            before.requestFocus();
        }
    }

    private static boolean isValidFocusMove(View before, View after) {
        return after != null && after != before && !shouldSkipFocusTarget(after, before);
    }

    private static KeyEvent createDpadEvent(long downTime, long eventTime, int action, int keyCode) {
        return new KeyEvent(
                downTime,
                eventTime,
                action,
                keyCode,
                0,
                0,
                -1,
                0,
                KeyEvent.FLAG_FROM_SYSTEM,
                InputDevice.SOURCE_DPAD
        );
    }

    private static boolean moveFocusByDirection(View focused, int direction) {
        View next = focused.focusSearch(direction);
        if (!isValidFocusMove(focused, next)) {
            return false;
        }
        if (next == null || next == focused) {
            return false;
        }
        if (next.requestFocus()) {
            TvRecyclerView rv = findAncestor(next, TvRecyclerView.class);
            scrollRecyclerToFocused(rv, next);
            return true;
        }
        return false;
    }

    private static boolean shouldSkipFocusTarget(View candidate, View current) {
        if (candidate == null || candidate == current) {
            return true;
        }
        return isContainerFocusTrap(candidate);
    }

    private static boolean isContainerFocusTrap(View view) {
        if (view == null || !view.isFocusable()) {
            return false;
        }
        String name = view.getClass().getName();
        if (name.contains("ViewPager") || name.contains("NestedScrollView") || name.contains("ScrollView")) {
            return isLargeContainer(view);
        }
        return false;
    }

    private static boolean isLargeContainer(View view) {
        View root = view.getRootView();
        if (root == null || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        long viewArea = (long) view.getWidth() * view.getHeight();
        long rootArea = (long) root.getWidth() * root.getHeight();
        return rootArea > 0 && viewArea > rootArea * 0.18f;
    }

    private static int keyCodeToDirection(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
                return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return View.FOCUS_DOWN;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return View.FOCUS_RIGHT;
            default:
                return View.FOCUS_FORWARD;
        }
    }

    private static View ensureFocusTarget(View root) {
        View focus = root.findFocus();
        if (focus != null) {
            return focus;
        }
        focus = findFirstFocusable(root);
        if (focus != null) {
            focus.requestFocus();
            return focus;
        }
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        return root;
    }

    private static View findFirstFocusable(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (root.isFocusable() && !isContainerFocusTrap(root)) {
            return root;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFirstFocusable(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static <T extends View> T findAncestor(View view, Class<T> type) {
        View current = view;
        while (current != null) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getParent() instanceof View) {
                current = (View) current.getParent();
            } else {
                break;
            }
        }
        return null;
    }

    public static String currentActivityName() {
        Activity activity = AppManager.getInstance().currentActivity();
        if (activity == null) {
            return "";
        }
        return activity.getClass().getSimpleName();
    }
}
