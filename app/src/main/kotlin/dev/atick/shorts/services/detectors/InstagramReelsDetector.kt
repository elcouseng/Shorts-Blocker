/*
 * Copyright 2025 Atick Faisal
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.atick.shorts.services.detectors

import android.content.res.Resources
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Detector for Instagram Reels short-form content.
 *
 * Fires when the user is either on the Reels tab (`clips_tab` selected in the
 * bottom nav) or inside Instagram's fullscreen Clips viewer (`clips_viewer_action_bar_title`
 * present in the tree — covers Reels opened from a DM, from search, etc.).
 */
class InstagramReelsDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.instagram.android"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo,
        resources: Resources,
    ): Boolean {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(rootNode)
        var nodesScanned = 0

        while (stack.isNotEmpty() && nodesScanned < 150) {
            val n = stack.removeFirst()
            nodesScanned++

            val id = n.viewIdResourceName?.lowercase()
            if (id != null) {
                if ("clips_tab" in id && n.isSelected) {
                    Timber.i("[Instagram] ✓ Reels tab is selected")
                    return true
                }
                if ("clips_viewer_action_bar_title" in id) {
                    Timber.i("[Instagram] ✓ Fullscreen Clips viewer detected")
                    return true
                }
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
        }

        return false
    }
}
