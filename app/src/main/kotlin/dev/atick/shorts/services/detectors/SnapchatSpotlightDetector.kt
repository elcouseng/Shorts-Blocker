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
 * Detector for Snapchat Spotlight and Stories Discover/Following sections.
 *
 * Fires when the user is on:
 * - The **Spotlight** tab (any sub-tab: For You / Following / Games), detected
 *   by the presence of a node with `text="Spotlight"`. The Spotlight sub-tab
 *   bar at the top of the tab uses `text` for its labels, while the bottom
 *   nav uses `content-desc` — so `text="Spotlight"` is unique to the Spotlight
 *   tab itself.
 * - The **Stories** tab scrolled to the **Discover** or **Following** section,
 *   detected by `text="Discover"` or `text="Following"` (the section headers).
 *
 * Friend story playback overlays the Stories tab content, so its accessibility
 * tree also contains those headers. To avoid blocking friends, we suppress the
 * Stories-section rule whenever the friend-story overlay marker
 * `content-desc="lens_cta_element"` is present.
 *
 * Friends, Chat, Camera, Map, and the Stories → Friends section are never
 * blocked: they don't carry any of the trigger texts.
 */
class SnapchatSpotlightDetector : ShortFormContentDetector {

    override fun getPackageName(): String = "com.snapchat.android"

    override fun isShortFormContent(
        event: AccessibilityEvent,
        rootNode: AccessibilityNodeInfo,
        resources: Resources,
    ): Boolean {
        var spotlightTabFound = false
        var discoverHeaderFound = false
        var followingHeaderFound = false
        var friendStoryOverlay = false

        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(rootNode)
        var nodesScanned = 0

        while (stack.isNotEmpty() && nodesScanned < 250) {
            val n = stack.removeFirst()
            nodesScanned++

            when (n.text?.toString()) {
                "Spotlight" -> spotlightTabFound = true
                "Discover" -> discoverHeaderFound = true
                "Following" -> followingHeaderFound = true
            }

            if (n.contentDescription?.toString() == "lens_cta_element") {
                friendStoryOverlay = true
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { stack.add(it) }
            }
        }

        if (spotlightTabFound) {
            Timber.i("[Snapchat] ✓ Spotlight tab detected")
            return true
        }

        if ((discoverHeaderFound || followingHeaderFound) && !friendStoryOverlay) {
            Timber.i(
                "[Snapchat] ✓ Stories Discover/Following section " +
                    "(discover=$discoverHeaderFound, following=$followingHeaderFound)",
            )
            return true
        }

        return false
    }
}
