package com.habittags.app

import android.content.Context
import android.widget.Toast

object TapHandler {
    fun handle(context: Context, tagId: String) {
        Toast.makeText(context, "Tag $tagId", Toast.LENGTH_SHORT).show()
    }
}
