package com.stuffy.noreels

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ReelsBlockerService : AccessibilityService() {

    companion object {
        const val IG = "com.instagram.android"
        const val PREFS = "noreels"

        // Подстроки id элементов Instagram. Если после обновления Instagram
        // блокировка перестала работать, правятся только эти списки.
        val VIEWER_MARKERS = listOf(
            "clips_viewer", "clips_swipe_refresh", "clips_video_container",
            "clips_item", "reel_viewer"
        )
        val DM_MARKERS = listOf(
            "row_thread_composer", "thread_composer", "direct_thread_view"
        )
    }

    private val main = Handler(Looper.getMainLooper())
    private var lastProcessed = 0L
    private var lastBlock = 0L
    private var lastDmSeen = 0L
    private var lastDump = 0L
    private var otherSince = 0L
    private var inViewer = false
    private var viewerAllowed = false

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != IG) return
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) return

        val now = System.currentTimeMillis()
        if (now - lastProcessed < 120) return
        lastProcessed = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != IG) return

        val ids = HashSet<String>()
        var tabSelected = false
        var count = 0

        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || count > 800 || depth > 40) return
            count++
            val id = n.viewIdResourceName
            if (id != null) {
                val short = id.substringAfter(":id/")
                ids.add(short)
                if (short == "clips_tab" && n.isSelected) tabSelected = true
            }
            val desc = n.contentDescription?.toString()
            if (desc != null && desc.startsWith("Reels") && n.isSelected) tabSelected = true
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)

        val hasViewer = ids.any { id -> VIEWER_MARKERS.any { id.contains(it) } }
        val hasDm = ids.any { id -> DM_MARKERS.any { id.contains(it) } }
        if (hasDm) lastDmSeen = now

        val screen = when {
            tabSelected -> "REELS_TAB"
            hasViewer -> "REELS_VIEWER"
            hasDm -> "DM"
            else -> "OTHER"
        }

        if (prefs.getBoolean("debug", false) && now - lastDump > 400) {
            lastDump = now
            prefs.edit()
                .putString("dump", "screen=$screen tab=$tabSelected allowed=$viewerAllowed\n" + ids.sorted().joinToString("\n"))
                .apply()
        }

        when (screen) {
            "REELS_TAB" -> {
                inViewer = false
                otherSince = 0
                block(root, true)
            }
            "REELS_VIEWER" -> {
                otherSince = 0
                if (!inViewer) {
                    inViewer = true
                    // Рилс разрешён только если мы только что были в переписке
                    viewerAllowed = hasDm || now - lastDmSeen < 4000
                }
                if (!viewerAllowed) block(root, false)
            }
            "DM" -> {
                inViewer = false
                otherSince = 0
            }
            else -> {
                if (otherSince == 0L) otherSince = now
                else if (now - otherSince > 1500) inViewer = false
            }
        }
    }

    private fun block(root: AccessibilityNodeInfo, tab: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastBlock < 700) return
        lastBlock = now

        var done = false
        if (tab) {
            var n: AccessibilityNodeInfo? =
                root.findAccessibilityNodeInfosByViewId("$IG:id/feed_tab").firstOrNull()
            while (n != null && !done) {
                if (n.isClickable) {
                    done = n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } else {
                    n = n.parent
                }
            }
        }
        if (!done) performGlobalAction(GLOBAL_ACTION_BACK)

        main.post {
            Toast.makeText(this, "Reels заблокированы", Toast.LENGTH_SHORT).show()
        }
    }
}
