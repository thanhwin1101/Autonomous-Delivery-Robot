package com.example.autodeliveryapp;

import android.app.Activity;
import android.view.View;
import android.view.Window;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
public final class EdgeToEdgeHelper {

    private EdgeToEdgeHelper() {

    }

    /**
     * Programmatically forces the status bar to be transparent and sets the system icon tints.
     */
    public static void setLightStatusBar(Activity activity, boolean isLight) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        View decorView = window.getDecorView();
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, decorView);
        controller.setAppearanceLightStatusBars(isLight);
    }

    /**
     *
     *
     *
     */
    public static void applySystemBarsPadding(View contentRoot) {
        if (contentRoot == null) return;


        final int originalPaddingTop    = contentRoot.getPaddingTop();
        final int originalPaddingBottom = contentRoot.getPaddingBottom();
        final int originalPaddingLeft   = contentRoot.getPaddingLeft();
        final int originalPaddingRight  = contentRoot.getPaddingRight();

        ViewCompat.setOnApplyWindowInsetsListener(contentRoot, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    originalPaddingLeft   + bars.left,
                    originalPaddingTop    + bars.top,
                    originalPaddingRight  + bars.right,
                    originalPaddingBottom + bars.bottom
            );
            return windowInsets;
        });
    }

    /**
     *
     *
     */
    public static void applyStatusBarPadding(View... views) {
        if (views == null || views.length == 0) return;
        for (View view : views) {
            if (view == null) continue;
            final int originalPaddingTop    = view.getPaddingTop();
            final int originalPaddingBottom = view.getPaddingBottom();
            final int originalPaddingLeft   = view.getPaddingLeft();
            final int originalPaddingRight  = view.getPaddingRight();

            ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(
                        originalPaddingLeft,
                        originalPaddingTop + bars.top,
                        originalPaddingRight,
                        originalPaddingBottom
                );
                return windowInsets;
            });
        }
    }

    /**
     *
     *
     *
     */
    public static void applyNavigationBarPadding(View bottomView) {
        if (bottomView == null) return;
        final int originalPaddingTop    = bottomView.getPaddingTop();
        final int originalPaddingBottom = bottomView.getPaddingBottom();
        final int originalPaddingLeft   = bottomView.getPaddingLeft();
        final int originalPaddingRight  = bottomView.getPaddingRight();

        ViewCompat.setOnApplyWindowInsetsListener(bottomView, (view, windowInsets) -> {
            Insets navBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.navigationBars() | WindowInsetsCompat.Type.ime());
            view.setPadding(
                    originalPaddingLeft,
                    originalPaddingTop,
                    originalPaddingRight,
                    originalPaddingBottom + navBars.bottom
            );
            return windowInsets;
        });
    }
}
