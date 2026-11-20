package com.habittags.app.block

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.habittags.app.R
import com.habittags.app.ui.theme.SpaceColour

/**
 * Full-screen cover drawn over a blocked app, in the running space's colour and the app's type.
 * Plain views: a service window has no Compose lifecycle.
 */
class BlockOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var titleView: TextView? = null
    private var detailView: TextView? = null
    private var button: TextView? = null
    private var shownColour: SpaceColour? = null

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), context.resources.displayMetrics).toInt()

    fun show(colour: SpaceColour, title: String, detail: String) {
        if (!Settings.canDrawOverlays(context)) {
            android.util.Log.w("BlockOverlay", "Overlay permission missing")
            return
        }
        if (view != null) {
            titleView?.text = title
            detailView?.text = detail
            if (colour != shownColour) paint(colour)
            return
        }
        val bold = ResourcesCompat.getFont(context, R.font.mise_bold)
        val regular = ResourcesCompat.getFont(context, R.font.mise_regular)
        val medium = ResourcesCompat.getFont(context, R.font.mise_medium)
        val t = TextView(context).apply {
            text = title
            textSize = 34f
            typeface = bold
            contentDescription = "blocked-message"
        }
        val d = TextView(context).apply {
            text = detail
            textSize = 18f
            typeface = regular
            setPadding(0, dp(12), 0, dp(40))
        }
        val home = TextView(context).apply {
            text = "Go to home screen"
            textSize = 17f
            typeface = medium
            gravity = Gravity.CENTER
            setPadding(dp(28), dp(16), dp(28), dp(16))
            isClickable = true
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding(dp(28), dp(28), dp(28), dp(28))
            addView(t)
            addView(d)
            addView(home, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        params.title = "HabitTagsBlockOverlay"
        view = root
        titleView = t
        detailView = d
        button = home
        paint(colour)
        // Permission can be revoked between the check and here; skip the cover rather than crash the service.
        runCatching { wm.addView(root, params) }.onFailure { hide() }
    }

    private fun paint(colour: SpaceColour) {
        val field = colour.field.toInt()
        val onField = colour.onField.toInt()
        view?.setBackgroundColor(field)
        titleView?.setTextColor(onField)
        detailView?.setTextColor(onField)
        button?.setTextColor(field)
        button?.background = GradientDrawable().apply {
            setColor(onField)
            cornerRadius = dp(28).toFloat()
        }
        shownColour = colour
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        titleView = null
        detailView = null
        button = null
        shownColour = null
    }
}
