package com.glove.browser

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.webkit.WebView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Pull-to-refresh only when the page is already at the top. */
class RefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {
    override fun canChildScrollUp(): Boolean {
        val frame = getChildAt(0) as? ViewGroup
        val web = frame?.getChildAt(0) as? WebView
        if (web != null) return web.scrollY > 0 || web.canScrollVertically(-1)
        return super.canChildScrollUp()
    }
}
