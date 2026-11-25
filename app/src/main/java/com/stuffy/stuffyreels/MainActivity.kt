package com.stuffy.stuffyreels

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(ReelsBlockerService.PREFS, MODE_PRIVATE)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val systemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        layout.addView(TextView(this).apply {
            text = "StuffyReels"
            textSize = 24f
        })
        layout.addView(TextView(this).apply {
            text = "Прячет кнопку Reels в нижней панели Instagram и блокирует нажатие на неё. " +
                "Больше ничего не трогает."
            setPadding(0, pad, 0, pad)
        })

        status = TextView(this).apply { textSize = 16f; setPadding(0, 0, 0, pad) }
        layout.addView(status)

        fun button(label: String, onClick: () -> Unit) {
            layout.addView(Button(this).apply {
                text = label
                setOnClickListener { onClick() }
            })
        }

        button("1. Включить службу (Спец. возможности)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button("Настройки приложения (если служба серая)") {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }

        layout.addView(Switch(this).apply {
            text = "Скрытие кнопки включено"
            isChecked = prefs.getBoolean("enabled", true)
            setPadding(0, pad, 0, pad)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("enabled", c).apply() }
        })

        layout.addView(Switch(this).apply {
            text = "Тёмная тема в Instagram (цвет заплатки чёрный)"
            isChecked = prefs.getBoolean("dark", systemDark)
            setPadding(0, 0, 0, pad)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("dark", c).apply() }
        })

        layout.addView(TextView(this).apply {
            text = "Текст на заплатке Reels (короткий, пусто = без надписи)"
        })
        layout.addView(EditText(this).apply {
            setText(prefs.getString("reels_text", ""))
            setSingleLine()
            filters = arrayOf(InputFilter.LengthFilter(20))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) {
                    prefs.edit().putString("reels_text", e?.toString() ?: "").apply()
                }
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        })

        layout.addView(Switch(this).apply {
            text = "Режим отладки (записывать id экрана)"
            isChecked = prefs.getBoolean("debug", false)
            setOnCheckedChangeListener { _, c -> prefs.edit().putBoolean("debug", c).apply() }
        })

        button("Скопировать id последнего экрана") {
            val dump = prefs.getString("dump", null)
            if (dump == null) {
                Toast.makeText(this, "Пока пусто: включи отладку и открой Instagram", Toast.LENGTH_LONG).show()
            } else {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("dump", dump))
                Toast.makeText(this, "Скопировано", Toast.LENGTH_SHORT).show()
            }
        }

        setContentView(ScrollView(this).apply { addView(layout) })
    }

    override fun onResume() {
        super.onResume()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )?.contains("$packageName/") == true
        status.text = if (enabled) "Служба: включена ✅" else "Служба: выключена ❌"
    }
}
