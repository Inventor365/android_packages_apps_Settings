/*
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settings.deviceinfo.firmwareversion

import android.content.Context
import android.os.SystemProperties
import com.android.settings.core.BasePreferenceController

/** Shows the device maintainer from ro.neoteric.maintainer, hidden when unset. */
class NeotericMaintainerPreferenceController(
    context: Context,
    preferenceKey: String
) : BasePreferenceController(context, preferenceKey) {

    override fun getAvailabilityStatus(): Int =
        if (maintainer().isEmpty()) UNSUPPORTED_ON_DEVICE else AVAILABLE_UNSEARCHABLE

    override fun getSummary(): CharSequence = maintainer()

    private fun maintainer(): String = SystemProperties.get(MAINTAINER_PROP, "")

    companion object {
        private const val MAINTAINER_PROP = "ro.neoteric.maintainer"
    }
}
