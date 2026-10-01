/*
 * SPDX-FileCopyrightText: Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.gestures;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

public class DoubleTapScreenToSleepPreferenceController extends GesturePreferenceController {

    private static final String PREF_KEY_VIDEO = "gesture_double_tap_screen_to_sleep_video";
    private static final String PREF_KEY_SWITCH = "gesture_double_tap_screen_to_sleep";

    public DoubleTapScreenToSleepPreferenceController(Context context, String key) {
        super(context, key);
    }

    @Override
    public int getAvailabilityStatus() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_supportDoubleTapSleep)
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }

    @Override
    public boolean isSliceable() {
        return TextUtils.equals(getPreferenceKey(), PREF_KEY_SWITCH);
    }

    @Override
    public boolean isPublicSlice() {
        return true;
    }

    @Override
    protected String getVideoPrefKey() {
        return PREF_KEY_VIDEO;
    }

    @Override
    public boolean isChecked() {
        return Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_SLEEP, 0) == 1;
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        return Settings.Secure.putInt(mContext.getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_SLEEP, isChecked ? 1 : 0);
    }
}
