package com.glove.browser

import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.glove.browser.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val store = BrowserStore(this)
        binding.back.setOnClickListener { finish() }
        binding.clearHistory.setOnClickListener {
            store.clearHistory()
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show()
        }
        binding.clearCookies.setOnClickListener {
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
            Toast.makeText(this, "Cookie очищены", Toast.LENGTH_SHORT).show()
        }
        binding.clearCache.setOnClickListener {
            WebView(this).apply {
                clearCache(true)
                destroy()
            }
            Toast.makeText(this, "Кэш очищен", Toast.LENGTH_SHORT).show()
        }
        val version = packageManager.getPackageInfo(packageName, 0).versionName
        binding.about.text = "Glove Browser $version\nПоиск Orion\nAndroid 8.0 и новее"
    }
}
