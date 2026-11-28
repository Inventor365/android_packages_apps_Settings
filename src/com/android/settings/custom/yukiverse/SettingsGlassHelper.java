/*
 * SPDX-FileCopyrightText: 2026 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.yukiverse;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.CrossWindowBlurListeners;
import android.view.View;
import android.view.ViewRootImpl;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.graphics.ColorUtils;

import com.android.internal.graphics.drawable.BackgroundBlurDrawable;
import com.android.internal.util.neoteric.YukiGlassConfig;
import com.android.settings.R;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settingslib.Utils;
import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * Applies Yuki's Addons system-wide Blur / Frosted Blur / Liquid Glass cleanly to Settings
 * activities over the live wallpaper without breaking SetupWizard, two-pane embedding, or
 * text contrast.
 */
public final class SettingsGlassHelper {

    private static final String[] OBSERVED_KEYS = new String[] {
        YukiGlassConfig.KEY_ENABLED,
        YukiGlassConfig.KEY_STYLE,
        YukiGlassConfig.KEY_INTENSITY,
        YukiGlassConfig.KEY_FROST,
        YukiGlassConfig.KEY_SETTINGS_MODE,
        YukiGlassConfig.KEY_SETTINGS_RADIUS,
        YukiGlassConfig.KEY_SETTINGS_OPACITY,
    };

    private final Activity mActivity;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private BackgroundBlurDrawable mBlurDrawable;
    private boolean mAppliedGlass = false;
    private boolean mObserverRegistered = false;
    private boolean mBlurEnabledBySystem = true;
    private java.util.function.Consumer<Boolean> mBlurSystemListener;

    private final ContentObserver mObserver = new ContentObserver(mHandler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            apply();
        }
    };

    public SettingsGlassHelper(Activity activity) {
        mActivity = activity;
    }

    public void onCreate() {
        if (shouldSkipCompletely()) {
            return;
        }
        final ContentResolver resolver = mActivity.getContentResolver();
        if (!mObserverRegistered) {
            for (String key : OBSERVED_KEYS) {
                resolver.registerContentObserver(
                        Settings.Secure.getUriFor(key), false, mObserver, UserHandle.USER_ALL);
            }
            mObserverRegistered = true;
        }
        if (CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED) {
            final CrossWindowBlurListeners listeners = CrossWindowBlurListeners.getInstance();
            mBlurEnabledBySystem = listeners.isCrossWindowBlurEnabled();
            mBlurSystemListener = enabled -> {
                mBlurEnabledBySystem = Boolean.TRUE.equals(enabled);
                apply();
            };
            listeners.addListener(mActivity.getMainExecutor(), mBlurSystemListener);
        } else {
            mBlurEnabledBySystem = false;
        }
        final View decor = mActivity.getWindow() != null ? mActivity.getWindow().getDecorView() : null;
        if (decor != null) {
            decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    apply();
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
            decor.post(this::apply);
        }
    }

    public void onResume() {
        if (!shouldSkipCompletely()) {
            apply();
        }
    }

    public void onDestroy() {
        if (mObserverRegistered) {
            mActivity.getContentResolver().unregisterContentObserver(mObserver);
            mObserverRegistered = false;
        }
        if (mBlurSystemListener != null && CrossWindowBlurListeners.CROSS_WINDOW_BLUR_SUPPORTED) {
            CrossWindowBlurListeners.getInstance().removeListener(mBlurSystemListener);
            mBlurSystemListener = null;
        }
        clearBlurDrawable();
    }

    public boolean isGlassActive() {
        return mAppliedGlass;
    }

    private boolean shouldSkipCompletely() {
        if (mActivity.isFinishing() || mActivity.isDestroyed()) {
            return true;
        }
        if (WizardManagerHelper.isAnySetupWizard(mActivity.getIntent())) {
            return true;
        }
        if (ActivityEmbeddingUtils.isAlreadyEmbedded(mActivity)) {
            return true;
        }
        ActivityManager am = mActivity.getSystemService(ActivityManager.class);
        return am != null && am.isLowRamDevice();
    }

    public void apply() {
        if (shouldSkipCompletely()) {
            return;
        }
        final Window window = mActivity.getWindow();
        if (window == null) {
            return;
        }
        final View decor = window.getDecorView();
        if (decor == null) {
            return;
        }

        final ContentResolver resolver = mActivity.getContentResolver();
        final int mode = mBlurEnabledBySystem
                ? YukiGlassConfig.resolveSurfaceMode(resolver, YukiGlassConfig.KEY_SETTINGS_MODE)
                : YukiGlassConfig.MODE_OFF;
        final int baseRadiusDp = YukiGlassConfig.getInt(
                resolver, YukiGlassConfig.KEY_SETTINGS_RADIUS,
                YukiGlassConfig.DEFAULT_SETTINGS_RADIUS, 0, 100);
        final int intensity = YukiGlassConfig.getIntensityPercent(resolver);
        final float density = mActivity.getResources().getDisplayMetrics().density;
        final int blurRadiusPx = Math.round(baseRadiusDp * density * (intensity / 100f));

        if (mode == YukiGlassConfig.MODE_OFF || blurRadiusPx <= 0) {
            if (mAppliedGlass) {
                restoreOpaque(window, decor);
            }
            return;
        }

        final ViewRootImpl vri = decor.getViewRootImpl();
        if (vri == null) {
            decor.post(this::apply);
            return;
        }

        final boolean darkTheme =
                (mActivity.getResources().getConfiguration().uiMode
                        & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        final int opacityPercent = YukiGlassConfig.getInt(
                resolver, YukiGlassConfig.KEY_SETTINGS_OPACITY,
                YukiGlassConfig.DEFAULT_SETTINGS_OPACITY, 40, 100);
        final int baseBgColor = Utils.getColorAttrDefaultColor(
                mActivity, android.R.attr.colorBackground);
        final int veilAlpha = Math.round((opacityPercent / 100f) * 255f);
        final int veilColor = ColorUtils.setAlphaComponent(baseBgColor, veilAlpha);
        final int material = YukiGlassConfig.panelMaterialFor(mode, darkTheme);

        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        window.setFormat(PixelFormat.TRANSLUCENT);

        if (mBlurDrawable == null) {
            mBlurDrawable = vri.createBackgroundBlurDrawable();
            if (mBlurDrawable == null) {
                return;
            }
            mBlurDrawable.setCornerRadius(0f);
        }
        mBlurDrawable.setMaterial(material);
        mBlurDrawable.setBlurRadius(blurRadiusPx);
        mBlurDrawable.setColor(veilColor);
        mBlurDrawable.setAlpha(255);
        mBlurDrawable.setVisible(true, false);

        window.setBackgroundDrawable(mBlurDrawable);
        clearOpaqueContainerBackgrounds(Color.TRANSPARENT);
        mAppliedGlass = true;
    }

    private void restoreOpaque(Window window, View decor) {
        clearBlurDrawable();
        window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
        final int color = Utils.getColorAttrDefaultColor(mActivity, android.R.attr.colorBackground);
        window.setBackgroundDrawable(new ColorDrawable(color));
        clearOpaqueContainerBackgrounds(color);
        mAppliedGlass = false;
    }

    private void clearBlurDrawable() {
        if (mBlurDrawable != null) {
            mBlurDrawable.setBlurRadius(0);
            mBlurDrawable.setAlpha(0);
            mBlurDrawable.setVisible(false, false);
            mBlurDrawable = null;
        }
    }

    private void clearOpaqueContainerBackgrounds(int color) {
        setViewBgIfPresent(android.R.id.content, color);
        setViewBgIfPresent(R.id.content_parent, color);
        setViewBgIfPresent(R.id.app_bar_container, color);
        setViewBgIfPresent(R.id.app_bar, color);
        setViewBgIfPresent(R.id.action_bar, color);
        setViewBgIfPresent(com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar, color);
        setViewBgIfPresent(R.id.settings_homepage_container, color);
        setViewBgIfPresent(R.id.main_content, color);
        View ctl = mActivity.findViewById(
                com.android.settingslib.collapsingtoolbar.R.id.collapsing_toolbar);
        if (ctl instanceof com.google.android.material.appbar.CollapsingToolbarLayout) {
            com.google.android.material.appbar.CollapsingToolbarLayout collapsing =
                    (com.google.android.material.appbar.CollapsingToolbarLayout) ctl;
            collapsing.setContentScrimColor(color);
            collapsing.setStatusBarScrimColor(color);
        }
    }

    private void setViewBgIfPresent(int id, int color) {
        View v = mActivity.findViewById(id);
        if (v != null) {
            v.setBackgroundColor(color);
        }
    }
}
