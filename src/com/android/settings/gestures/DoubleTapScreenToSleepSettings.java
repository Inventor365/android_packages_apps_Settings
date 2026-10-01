/*
 * SPDX-FileCopyrightText: Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.gestures;

import android.app.settings.SettingsEnums;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class DoubleTapScreenToSleepSettings extends DashboardFragment {

    private static final String TAG = "DoubleTapScreenToSleepSettings";

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURES;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.double_tap_screen_to_sleep_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.double_tap_screen_to_sleep_settings);
}
