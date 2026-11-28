/*
 * SPDX-FileCopyrightText: 2026 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.yukiverse;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemProperties;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.neoteric.HomeLauncherUtils;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class YukiVerseLauncherSettings extends DashboardFragment
        implements Preference.OnPreferenceChangeListener {

    private static final String TAG = "YukiVerseLauncherSettings";
    private static final String KEY_HOME_LAUNCHER = HomeLauncherUtils.PROP_ACTIVE_LAUNCHER;
    private static final String KEY_MODS_CATEGORY = "yukiverse_launcher_mods_category";
    private static final String KEY_PIXEL_LAUNCHER_ENHANCED = "yukiverse_pixel_launcher_enhanced";
    private static final String PLE_PACKAGE = "com.drdisagree.pixellauncherenhanced";
    private static final String KEY_BLUR_CATEGORY = "yukiverse_blur_category";
    private static final String[] PIXEL_BLUR_KEYS = new String[] {
        "yuki_glass_recents_mode",
        "yuki_glass_recents_radius",
        "yuki_glass_drawer_mode",
        "yuki_glass_drawer_radius",
    };

    private ListPreference mLauncherPref;
    private Preference mPixelLauncherEnhancedPref;
    private PreferenceCategory mBlurCategory;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = getContext();
        if (context == null) {
            return;
        }

        final int activeLauncher = HomeLauncherUtils.getActiveLauncherIndex();
        final boolean pixelShipped = HomeLauncherUtils.isPixelLauncherShipped();

        mLauncherPref = findPreference(KEY_HOME_LAUNCHER);
        if (mLauncherPref != null) {
            Resources res = context.getResources();
            String[] allEntries = res.getStringArray(R.array.yukiverse_home_launcher_entries);
            String[] allValues = res.getStringArray(R.array.yukiverse_home_launcher_values);

            List<CharSequence> entries = new ArrayList<>();
            List<CharSequence> values = new ArrayList<>();
            entries.add(allEntries[0]);
            values.add(allValues[0]);
            if (pixelShipped && allEntries.length > 1 && allValues.length > 1) {
                entries.add(allEntries[1]);
                values.add(allValues[1]);
            }

            mLauncherPref.setEntries(entries.toArray(new CharSequence[0]));
            mLauncherPref.setEntryValues(values.toArray(new CharSequence[0]));
            mLauncherPref.setValue(String.valueOf(
                    (pixelShipped && activeLauncher == HomeLauncherUtils.LAUNCHER_PIXEL)
                            ? HomeLauncherUtils.LAUNCHER_PIXEL
                            : HomeLauncherUtils.LAUNCHER_NEOTERIC));
            mLauncherPref.setOnPreferenceChangeListener(this);
        }

        mPixelLauncherEnhancedPref = findPreference(KEY_PIXEL_LAUNCHER_ENHANCED);
        if (mPixelLauncherEnhancedPref != null) {
            if (!pixelShipped || !isPackageInstalled(context, PLE_PACKAGE)) {
                PreferenceCategory category = findPreference(KEY_MODS_CATEGORY);
                if (category != null) {
                    getPreferenceScreen().removePreference(category);
                } else {
                    getPreferenceScreen().removePreference(mPixelLauncherEnhancedPref);
                }
                mPixelLauncherEnhancedPref = null;
            } else {
                updateEnhancedSummary(activeLauncher);
            }
        }

        mBlurCategory = findPreference(KEY_BLUR_CATEGORY);
        if (!pixelShipped && mBlurCategory != null) {
            for (String key : PIXEL_BLUR_KEYS) {
                Preference p = mBlurCategory.findPreference(key);
                if (p != null) {
                    mBlurCategory.removePreference(p);
                }
            }
        }
        updatePixelBlurHints(activeLauncher);
    }

    @Override
    public void onResume() {
        super.onResume();
        int activeLauncher = HomeLauncherUtils.getActiveLauncherIndex();
        updateEnhancedSummary(activeLauncher);
        updatePixelBlurHints(activeLauncher);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mLauncherPref) {
            int value = Integer.parseInt((String) newValue);
            updateEnhancedSummary(value);
            updatePixelBlurHints(value);
            showRebootDialog();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference != null && KEY_PIXEL_LAUNCHER_ENHANCED.equals(preference.getKey())) {
            handlePixelLauncherEnhancedClick();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void updateEnhancedSummary(int launcherIndex) {
        if (mPixelLauncherEnhancedPref == null) {
            return;
        }
        boolean pixelSelected = launcherIndex == HomeLauncherUtils.LAUNCHER_PIXEL;
        mPixelLauncherEnhancedPref.setSummary(pixelSelected
                ? R.string.yukiverse_pixel_launcher_enhanced_summary_active
                : R.string.yukiverse_pixel_launcher_enhanced_summary_warning);
    }

    private void updatePixelBlurHints(int launcherIndex) {
        if (mBlurCategory == null) {
            return;
        }
        boolean pixelSelected = launcherIndex == HomeLauncherUtils.LAUNCHER_PIXEL;
        for (String key : PIXEL_BLUR_KEYS) {
            Preference p = mBlurCategory.findPreference(key);
            if (p != null && !(p instanceof ListPreference)) {
                p.setSummary(pixelSelected ? null : getString(R.string.yukiverse_blur_pixel_only_note));
            }
        }
    }

    private void handlePixelLauncherEnhancedClick() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        if (HomeLauncherUtils.isPixelLauncherSelected()) {
            launchPixelLauncherEnhanced(context);
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.yukiverse_pixel_launcher_required_title)
                .setMessage(R.string.yukiverse_pixel_launcher_required_message)
                .setPositiveButton(R.string.yukiverse_switch_to_pixel_launcher, (dialog, which) -> {
                    SystemProperties.set(KEY_HOME_LAUNCHER,
                            String.valueOf(HomeLauncherUtils.LAUNCHER_PIXEL));
                    if (mLauncherPref != null) {
                        mLauncherPref.setValue(String.valueOf(HomeLauncherUtils.LAUNCHER_PIXEL));
                    }
                    updateEnhancedSummary(HomeLauncherUtils.LAUNCHER_PIXEL);
                    showRebootDialog();
                })
                .setNegativeButton(R.string.yukiverse_open_anyway, (dialog, which) ->
                        launchPixelLauncherEnhanced(context))
                .show();
    }

    private void showRebootDialog() {
        final Context context = getContext();
        if (context == null) {
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.yukiverse_launcher_restart_title)
                .setMessage(R.string.yukiverse_launcher_restart_message)
                .setPositiveButton(R.string.yukiverse_launcher_restart_now, (dialog, which) -> {
                    PowerManager pm = context.getSystemService(PowerManager.class);
                    if (pm != null) {
                        pm.reboot(null);
                    }
                })
                .setNegativeButton(R.string.yukiverse_launcher_restart_later, null)
                .show();
    }

    private static void launchPixelLauncherEnhanced(Context context) {
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(PLE_PACKAGE);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }

    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            return context.getPackageManager().getApplicationInfo(packageName, 0).enabled;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.VIEW_UNKNOWN;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.yukiverse_launcher_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.yukiverse_launcher_settings);
}
