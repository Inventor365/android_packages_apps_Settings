/*
 * Copyright (C) 2024 The LineageOS Project
 * Copyright (C) 2025-2026 Neoteric OS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.display.darkmode

import android.content.Context
import android.provider.Settings
import com.android.settings.R
import com.android.settingslib.datastore.KeyValueStore
import com.android.settingslib.datastore.SettingsSecureStore
import com.android.settingslib.metadata.ReadWritePermit
import com.android.settingslib.metadata.SensitivityLevel
import com.android.settingslib.metadata.SwitchPreference
import com.android.settingslib.metadata.preferencesapi.preconditions.PreconditionStability
import com.android.settingslib.preference.SwitchPreferenceBinding

class DarkModeBlackThemePreference(
    context: Context,
    private val darkModeStorage: DarkModeStorage = DarkModeStorage(context),
) :
    SwitchPreference(
        key = KEY,
        purpose = R.string.berry_black_theme_summary,
        title = R.string.berry_black_theme_title,
        summary = R.string.berry_black_theme_summary,
    ),
    SwitchPreferenceBinding {

    override fun dependencies(context: Context) = arrayOf(DarkModeMainSwitchPreference.KEY)

    override fun getEnabledDescription(): String = "Dark theme must be enabled."

    override fun getEnabledStability() = PreconditionStability.UNSTABLE

    override fun isEnabled(context: Context): Boolean =
        darkModeStorage.getBoolean(DarkModeMainSwitchPreference.KEY) == true

    override fun storage(context: Context): KeyValueStore = context.dataStore

    override fun getReadPermissions(context: Context) = SettingsSecureStore.getReadPermissions()

    override fun getWritePermissions(context: Context) = SettingsSecureStore.getWritePermissions()

    override fun getReadPermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override fun getWritePermit(context: Context, callingPid: Int, callingUid: Int) =
        ReadWritePermit.ALLOW

    override val sensitivityLevel
        get() = SensitivityLevel.NO_SENSITIVITY

    companion object {
        const val KEY = Settings.Secure.BERRY_BLACK_THEME

        private val Context.dataStore: KeyValueStore
            get() = SettingsSecureStore.get(this).apply { setDefaultValue(KEY, false) }
    }
}
