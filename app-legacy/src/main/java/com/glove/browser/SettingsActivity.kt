package com.glove.browser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.glove.browser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val store = BrowserStore(this)
        binding.back.setOnClickListener { finish() }
        paintDark(binding, store)
        binding.darkTheme.setOnClickListener {
            store.forceDark = !store.forceDark
            AppCompatDelegate.setDefaultNightMode(
                if (store.forceDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            )
            paintDark(binding, store)
        }
        binding.clearHistory.setOnClickListener {
            store.clearHistory()
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
        }
        binding.clearCookies.setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Cookie очищены", Toast.LENGTH_SHORT).show()
        }
        binding.passwords.setOnClickListener { showPasswords() }
        binding.permissions.setOnClickListener { showPermissions() }
        binding.clearCache.setOnClickListener {
            WebView(this).apply {
                clearCache(true)
                destroy()
            }
            Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show()
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        binding.about.text = "Glove Browser $version\nПоиск: Яндекс\nAndroid 8.0 и новее"
    }

    private fun showPasswords() {
        val items = Vault.all(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "Сохранённых паролей пока нет", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { entry ->
            val host = android.net.Uri.parse(entry.origin).host ?: entry.origin
            if (entry.username.isBlank()) host else "$host · ${entry.username}"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Пароли")
            .setItems(labels) { _, which ->
                val entry = items[which]
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(labels[which])
                    .setMessage(entry.password)
                    .setPositiveButton("Удалить") { _, _ ->
                        Vault.delete(this, entry.origin, entry.username)
                        Toast.makeText(this, "Пароль удалён", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Закрыть", null)
                    .show()
            }
            .show()
    }

    private fun showPermissions() {
        val items = SiteAccess.all(this)
        if (items.isEmpty()) {
            Toast.makeText(this, "Сайты ещё ничего не запрашивали", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = items.map { (origin, kind, allow) ->
            val host = android.net.Uri.parse(origin).host ?: origin
            val state = if (allow) "разрешено" else "запрещено"
            "$host · ${SiteAccess.label(kind)} · $state"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Разрешения сайтов")
            .setItems(labels) { _, which ->
                val row = items[which]
                SiteAccess.clear(this, row.first, row.second)
                Toast.makeText(this, "Разрешение сброшено", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun paintDark(binding: ActivitySettingsBinding, store: BrowserStore) {
        binding.darkTheme.text = if (store.forceDark) "Тёмная тема: включена" else "Тёмная тема: как в системе"
    }
}
