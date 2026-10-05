package com.glove.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * Pull-to-refresh only when:
 * - the page is already at the very top
 * - the gesture starts in the top edge band (above mid-screen, near the top)
 * - the touch is not inside a nested scrollable / overlay that can scroll up
 */
class RefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {
    var atTop: () -> Boolean = { true }
    private var allowGesture = false

    override fun canChildScrollUp(): Boolean = !atTop() || !allowGesture

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> allowGesture = canStartRefresh(ev)
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

    private fun canStartRefresh(ev: MotionEvent): Boolean {
        if (!atTop()) return false
        val edge = height * 0.22f
        if (ev.y > edge || ev.y > height * 0.5f) return false
        val target = findTouchedChild(this, ev.rawX, ev.rawY) ?: return true
        return !blocksRefresh(target)
    }

    private fun findTouchedChild(root: ViewGroup, rawX: Float, rawY: Float): View? {
        val loc = IntArray(2)
        for (i in root.childCount - 1 downTo 0) {
            val child = root.getChildAt(i) ?: continue
            if (child.visibility != View.VISIBLE) continue
            child.getLocationOnScreen(loc)
            val left = loc[0].toFloat()
            val top = loc[1].toFloat()
            val right = left + child.width
            val bottom = top + child.height
            if (rawX < left || rawX > right || rawY < top || rawY > bottom) continue
            if (child is ViewGroup) {
                findTouchedChild(child, rawX, rawY)?.let { return it }
            }
            return child
        }
        return null
    }

    private fun blocksRefresh(view: View): Boolean {
        var current: View? = view
        while (current != null && current !== this) {
            if (current.canScrollVertically(-1) || current.canScrollVertically(1)) {
                if (current.canScrollVertically(-1)) return true
                val cls = current.javaClass.name
                if (cls.contains("Popup", true) ||
                    cls.contains("Dialog", true) ||
                    cls.contains("Drawer", true) ||
                    cls.contains("BottomSheet", true) ||
                    cls.contains("ListView", true) ||
                    cls.contains("RecyclerView", true) ||
                    cls.contains("ScrollView", true) ||
                    cls.contains("NestedScroll", true)
                ) {
                    return true
                }
            }
            val parent = current.parent
            current = parent as? View
        }
        return false
    }
}
