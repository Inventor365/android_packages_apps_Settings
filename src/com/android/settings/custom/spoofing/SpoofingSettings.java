/*
 * SPDX-FileCopyrightText: 2026 Neoteric OS
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.custom.spoofing;

import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.custom.PixelPropsUtils;
import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class SpoofingSettings extends DashboardFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "SpoofingSettings";

    private static final String KEY_TENSOR_SPOOF = "tensor_spoof";
    private static final String KEY_PHOTOS_SPOOF = "pi_photos_spoof";
    private static final String KEY_SNAPCHAT_SPOOF = "pi_snapchat_spoof";
    private static final String KEY_RESET_DEFAULTS = "spoofing_reset_defaults";

    private static final String PHOTOS_PACKAGE = "com.google.android.apps.photos";
    private static final String SNAPCHAT_PACKAGE = "com.snapchat.android";
    private static final String[] GMS_FAMILY = {
            "com.android.vending",
            "com.google.android.gms.unstable",
            "com.google.android.gms",
            "com.google.android.gms.persistent",
            "com.google.android.rkpdapp",
            "com.google.android.gsf",
            "com.google.android.contactkeys",
            "com.google.android.safetycore",
            "com.google.android.googlequicksearchbox",
    };

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mPendingKill;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (PixelPropsUtils.isTensorPixelDevice()) {
            Preference tensorPref = findPreference(KEY_TENSOR_SPOOF);
            if (tensorPref != null) {
                tensorPref.setVisible(false);
            }
        }

        Preference photosPref = findPreference(KEY_PHOTOS_SPOOF);
        if (photosPref != null) {
            photosPref.setOnPreferenceChangeListener(this);
        }
        Preference snapPref = findPreference(KEY_SNAPCHAT_SPOOF);
        if (snapPref != null) {
            snapPref.setOnPreferenceChangeListener(this);
        }
        Preference resetPref = findPreference(KEY_RESET_DEFAULTS);
        if (resetPref != null) {
            resetPref.setOnPreferenceClickListener(p -> {
                showResetDefaultsDialog();
                return true;
            });
        }
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        if (KEY_PHOTOS_SPOOF.equals(key)) {
            boolean enabled = Boolean.TRUE.equals(newValue);
            Settings.Secure.putInt(requireContext().getContentResolver(),
                    Settings.Secure.SPOOF_PIF_PHOTOS, enabled ? 1 : 0);
            scheduleForceStop(PHOTOS_PACKAGE);
            return true;
        } else if (KEY_SNAPCHAT_SPOOF.equals(key)) {
            scheduleForceStop(SNAPCHAT_PACKAGE);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mPendingKill != null) {
            mHandler.removeCallbacks(mPendingKill);
            mPendingKill = null;
        }
    }

    private void scheduleForceStop(String... packages) {
        if (mPendingKill != null) {
            mHandler.removeCallbacks(mPendingKill);
        }
        Toast.makeText(requireContext(),
                R.string.spoofing_applying_changes, Toast.LENGTH_SHORT).show();
        final Context appContext = requireContext().getApplicationContext();
        mPendingKill = () -> {
            ActivityManager am = appContext.getSystemService(ActivityManager.class);
            if (am == null) return;
            for (String pkg : packages) {
                try {
                    am.forceStopPackage(pkg);
                } catch (Exception ignored) {
                }
            }
        };
        mHandler.postDelayed(mPendingKill, 800);
    }

    private void showResetDefaultsDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.spoofing_reset_defaults_dialog_title)
                .setMessage(R.string.spoofing_reset_defaults_dialog_message)
                .setPositiveButton(R.string.spoofing_reset_defaults_confirm, (d, w) -> {
                    resetAllSpoofingDefaults();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void resetAllSpoofingDefaults() {
        final ContentResolver cr = requireContext().getContentResolver();
        Settings.System.putInt(cr, "spoof_pif_enabled", 1);
        Settings.System.putInt(cr, "spoof_trickystore_enabled", 1);
        Settings.Secure.putString(cr, Settings.Secure.SPOOF_PIF_CONFIG, null);
        Settings.Secure.putLong(cr, PlayIntegrityFix.LAST_AUTO_FETCH_KEY, 0L);
        Settings.Secure.putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_TARGET, null);
        Settings.Secure.putString(cr, Settings.Secure.SPOOF_TRICKYSTORE_PATCH, "");
        Settings.Secure.putInt(cr, Settings.Secure.SPOOF_DUCK_RKP_ENABLED, 0);
        Settings.Secure.putInt(cr, Settings.Secure.PI_PP_SPOOF, 0);
        Settings.Secure.putString(cr, "pi_pp_targets", "");
        Settings.Secure.putInt(cr, Settings.Secure.PI_TENSOR_SPOOF, 0);
        Settings.Secure.putString(cr, "tensor_targets", "");
        Settings.Secure.putInt(cr, Settings.Secure.PI_PHOTOS_SPOOF, 0);
        Settings.Secure.putInt(cr, Settings.Secure.SPOOF_PIF_PHOTOS, 0);
        Settings.Secure.putInt(cr, Settings.Secure.PI_SNAPCHAT_SPOOF, 0);
        try {
            ActivityManager.getService().setDuckRkpEnabled(false);
            ActivityManager.getService().setSpoofTrickyStoreKeyBox("");
        } catch (Exception ignored) {
        }
        String[] allPkgs = new String[GMS_FAMILY.length + 2];
        System.arraycopy(GMS_FAMILY, 0, allPkgs, 0, GMS_FAMILY.length);
        allPkgs[GMS_FAMILY.length] = PHOTOS_PACKAGE;
        allPkgs[GMS_FAMILY.length + 1] = SNAPCHAT_PACKAGE;
        scheduleForceStop(allPkgs);
        Toast.makeText(requireContext(),
                R.string.spoofing_reset_defaults_done, Toast.LENGTH_SHORT).show();
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
        return R.xml.spoofing_settings;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.spoofing_settings);
}
