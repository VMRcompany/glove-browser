package com.glove.browser

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.glove.browser.databinding.ActivityPagesBinding

class PagesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityPagesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val store = BrowserStore(this)
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_HISTORY
        val items = when (mode) {
            MODE_BOOKMARKS -> store.bookmarks()
            MODE_DOWNLOADS -> store.downloads()
            else -> store.history()
        }
        binding.title.setText(
            when (mode) {
                MODE_BOOKMARKS -> R.string.bookmarks
                MODE_DOWNLOADS -> R.string.downloads
                else -> R.string.history
            }
        )
        binding.back.setOnClickListener { finish() }
        binding.clear.setOnClickListener {
            when (mode) {
                MODE_BOOKMARKS -> store.clearBookmarks()
                MODE_DOWNLOADS -> store.clearDownloads()
                else -> store.clearHistory()
            }
            finish()
        }
        if (items.isEmpty()) Toast.makeText(this, R.string.empty, Toast.LENGTH_SHORT).show()
        binding.list.adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val row = convertView ?: layoutInflater.inflate(R.layout.item_link, parent, false)
                val item = items[position]
                row.findViewById<TextView>(R.id.title).text = item.title.ifBlank { item.url }
                row.findViewById<TextView>(R.id.url).text = item.url
                return row
            }
        }
        binding.list.setOnItemClickListener { _, _, position, _ ->
            setResult(RESULT_OK, Intent().putExtra(EXTRA_URL, items[position].url))
            finish()
        }
    }

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_URL = "url"
        const val MODE_HISTORY = "history"
        const val MODE_BOOKMARKS = "bookmarks"
        const val MODE_DOWNLOADS = "downloads"
    }
}
