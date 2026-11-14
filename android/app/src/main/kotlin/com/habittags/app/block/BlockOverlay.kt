package com.habittags.app.block

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Full-screen cover drawn over a blocked app. Plain views: a service window has no Compose lifecycle. */
class BlockOverlay(private val context: Context) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var view: LinearLayout? = null
    private var message: TextView? = null

    fun show(text: String) {
        if (!Settings.canDrawOverlays(context)) return
        view?.let { message?.text = text; return }
        val msg = TextView(context).apply {
            this.text = text
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            contentDescription = "blocked-message"
        }
        val home = Button(context).apply {
            this.text = "Go to home screen"
            setOnClickListener {
                context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(20, 24, 33))
            setPadding(64, 64, 64, 64)
            addView(msg)
            addView(home)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        )
        params.title = "HabitTagsBlockOverlay"
        wm.addView(root, params)
        view = root
        message = msg
    }

    fun hide() {
        view?.let { wm.removeView(it) }
        view = null
        message = null
    }
}
