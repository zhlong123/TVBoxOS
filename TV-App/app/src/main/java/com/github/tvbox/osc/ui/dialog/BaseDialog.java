package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.R;

import java.lang.ref.WeakReference;

import xyz.doikki.videoplayer.util.CutoutUtil;

public class BaseDialog extends Dialog {

    @Nullable
    private static WeakReference<BaseDialog> sTopDialog;
    public BaseDialog(@NonNull Context context) {
        super(context, R.style.CustomDialogStyle);
    }

    public BaseDialog(Context context, int customDialogStyle) {
        super(context, customDialogStyle);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        CutoutUtil.adaptCutoutAboveAndroidP(this, true);//设置刘海
        super.onCreate(savedInstanceState);
    }

    @Override
    public void show() {
        if (isContextInvalid()) {
            return;
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        super.show();
        hideSysBar();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        sTopDialog = new WeakReference<>(this);
        requestDialogFocus();
    }

    @Override
    public void dismiss() {
        clearTopDialogIfSelf();
        super.dismiss();
    }

    @Override
    public void cancel() {
        clearTopDialogIfSelf();
        super.cancel();
    }

    @Nullable
    public static View getTopShowingDecorView() {
        BaseDialog dialog = sTopDialog != null ? sTopDialog.get() : null;
        if (dialog == null || !dialog.isShowing() || dialog.getWindow() == null) {
            return null;
        }
        return dialog.getWindow().getDecorView();
    }

    private void clearTopDialogIfSelf() {
        if (sTopDialog != null && sTopDialog.get() == this) {
            sTopDialog = null;
        }
    }

    private void requestDialogFocus() {
        View decor = getWindow().getDecorView();
        decor.post(() -> {
            View focus = findFirstFocusable(decor);
            if (focus != null) {
                focus.requestFocus();
            } else {
                decor.requestFocus();
            }
        });
    }

    @Nullable
    private static View findFirstFocusable(View root) {
        if (root == null || root.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (root.isFocusable()) {
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

    private boolean isContextInvalid() {
        Context context = getContext();
        if (!(context instanceof Activity)) {
            return false;
        }
        Activity activity = (Activity) context;
        return activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed());
    }

    private void hideSysBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int uiOptions = getWindow().getDecorView().getSystemUiVisibility();
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
            uiOptions |= View.SYSTEM_UI_FLAG_FULLSCREEN;
            getWindow().getDecorView().setSystemUiVisibility(uiOptions);
        }
    }
}
