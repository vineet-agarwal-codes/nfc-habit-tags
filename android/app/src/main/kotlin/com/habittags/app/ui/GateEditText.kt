package com.habittags.app.ui

import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import com.habittags.core.gate.FrictionGate

/**
 * Text field for the friction gate: no paste, no suggestions, and any edit that inserts more than a few
 * characters at once (paste, dictation, suggestion taps, swipe words) is reverted.
 */
class GateEditText(context: Context, val target: String) : EditText(context) {
    var onProgress: (typed: String, rejected: Boolean) -> Unit = { _, _ -> }
    private var accepted = ""
    private var reverting = false

    init {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        privateImeOptions = "nm,com.google.android.inputmethod.latin.noMicrophoneKey"
        isLongClickable = false
        setTextIsSelectable(false)
        contentDescription = "gate-input"
        val noMenu = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false
            override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?) = false
            override fun onDestroyActionMode(mode: ActionMode?) = Unit
        }
        customSelectionActionModeCallback = noMenu
        customInsertionActionModeCallback = noMenu
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                if (reverting) return
                val now = s.toString()
                if (FrictionGate.acceptEdit(accepted, now)) {
                    accepted = now
                    onProgress(now, false)
                } else {
                    reverting = true
                    setText(accepted)
                    setSelection(accepted.length)
                    reverting = false
                    onProgress(accepted, true)
                }
            }
        })
    }

    override fun onTextContextMenuItem(id: Int): Boolean =
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) false else super.onTextContextMenuItem(id)
}
