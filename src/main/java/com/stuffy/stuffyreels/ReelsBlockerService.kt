package com.stuffy.stuffyreels

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * Находит кнопку Reels в нижней панели Instagram и кладёт поверх неё
 * непрозрачную заплатку цвета фона, которая съедает нажатия.
 * Никого никуда не выкидывает.
 */
class ReelsBlockerService : AccessibilityService() {

    companion object {
        const val IG = "com.instagram.android"
        const val PREFS = "stuffyreels"
    }

    private val main = Handler(Looper.getMainLooper())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private var overlay: View? = null
    private val shown = Rect()
    private var shownDark: Boolean? = null
    private var shownText: String? = null
    private var lastCheck = 0L
    private var pending = false
    private val checkRunnable = Runnable { pending = false; check() }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        hide()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val now = SystemClock.uptimeMillis()
        if (now - lastCheck >= 80) {
            check()
        } else if (!pending) {
            pending = true
            main.postDelayed(checkRunnable, 80)
        }
    }

    private fun systemDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun check() {
        lastCheck = SystemClock.uptimeMillis()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (!prefs.getBoolean("enabled", true)) { hide(); return }

        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != IG) { hide(); return }

        val dm = resources.displayMetrics
        val r = Rect()
        var found = false

        // 1) по id вкладки
        val byId = root.findAccessibilityNodeInfosByViewId("$IG:id/clips_tab")
        for (n in byId) {
            if (!n.isVisibleToUser) continue
            n.getBoundsInScreen(r)
            if (!r.isEmpty && r.top > dm.heightPixels * 0.7) { found = true; break }
        }

        // 2) запасной вариант: нижний элемент с подписью "Reels"
        if (!found) {
            for (n in root.findAccessibilityNodeInfosByText("Reels")) {
                if (!n.isVisibleToUser) continue
                n.getBoundsInScreen(r)
                if (!r.isEmpty && r.top > dm.heightPixels * 0.85 && r.width() < dm.widthPixels / 3) {
                    found = true
                    break
                }
            }
        }

        if (prefs.getBoolean("debug", false)) dump(prefs, root, found, r)

        if (found) show(r, prefs.getBoolean("dark", systemDark()), prefs.getString("reels_text", "") ?: "") else hide()
    }

    private fun show(r: Rect, dark: Boolean, text: String) {
        try {
            val bg = if (dark) Color.BLACK else Color.WHITE
            val fg = if (dark) Color.LTGRAY else Color.DKGRAY
            val v = overlay
            if (v == null) {
                val nv = TextView(this).apply {
                    setBackgroundColor(bg)
                    setTextColor(fg)
                    this.text = text
                    gravity = Gravity.CENTER
                    maxLines = 2
                    setPadding(4, 4, 4, 4)
                    setAutoSizeTextTypeUniformWithConfiguration(
                        6, 14, 1, TypedValue.COMPLEX_UNIT_SP
                    )
                    setOnTouchListener { _, _ -> true }
                }
                val lp = WindowManager.LayoutParams(
                    r.width(), r.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.OPAQUE
                )
                lp.gravity = Gravity.TOP or Gravity.START
                lp.x = r.left
                lp.y = r.top
                wm.addView(nv, lp)
                overlay = nv
            } else if (r != shown || dark != shownDark || text != shownText) {
                val tv = v as TextView
                tv.setBackgroundColor(bg)
                tv.setTextColor(fg)
                tv.text = text
                val lp = tv.layoutParams as WindowManager.LayoutParams
                lp.width = r.width()
                lp.height = r.height()
                lp.x = r.left
                lp.y = r.top
                wm.updateViewLayout(tv, lp)
            }
            shown.set(r)
            shownDark = dark
            shownText = text
        } catch (e: Exception) {
            overlay = null
        }
    }

    private fun hide() {
        main.removeCallbacks(checkRunnable)
        pending = false
        val v = overlay ?: return
        try { wm.removeView(v) } catch (e: Exception) {}
        overlay = null
        shownDark = null
        shownText = null
    }

    // Режим отладки: сохраняет id элементов экрана, чтобы можно было скопировать их
    private fun dump(prefs: android.content.SharedPreferences, root: AccessibilityNodeInfo, found: Boolean, r: Rect) {
        val ids = HashSet<String>()
        var count = 0
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || count > 800 || depth > 40) return
            count++
            n.viewIdResourceName?.let { ids.add(it.substringAfter(":id/")) }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        prefs.edit()
            .putString("dump", "tabFound=$found rect=$r\n" + ids.sorted().joinToString("\n"))
            .apply()
    }
}
