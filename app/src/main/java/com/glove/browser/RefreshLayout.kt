package com.glove.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/** Refresh only when the gesture starts with the page already at the top. */
class RefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {
    var atTop: () -> Boolean = { true }
    private var allowGesture = false

    override fun canChildScrollUp(): Boolean = !atTop()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> allowGesture = atTop()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!allowGesture) return false
                val handled = super.onInterceptTouchEvent(ev)
                allowGesture = false
                return handled
            }
        }
        if (!allowGesture) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!allowGesture) return false
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
            allowGesture = false
        }
        return handled
    }
}
