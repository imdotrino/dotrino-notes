package com.dotrino.notes

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

// Small, common view pieces: plain native views, no AppCompat nor Material (cold start and
// size, CONVENCIONES §16.2), with the «Cool & Cozy» shapes: pills and 16 dp cards.

fun Context.px(v: Number) = (v.toFloat() * resources.displayMetrics.density).toInt()
fun Context.col(id: Int) = getColor(id)

fun rounded(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    cornerRadius = radius.toFloat(); setColor(color)
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.label(text: String, sp: Float, color: Int = col(R.color.n_text), bold: Boolean = false) = TextView(this).apply {
    this.text = text
    setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    setLineSpacing(0f, 1.15f)
}

/** A pill button. `filled` = the main action (accent). */
fun Context.pill(text: String, filled: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
    this.text = text
    gravity = Gravity.CENTER
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(col(if (filled) R.color.n_on_accent else R.color.n_text))
    background = if (filled) rounded(col(R.color.n_accent), px(24)) else rounded(col(R.color.n_card), px(24), px(1), col(R.color.n_line2))
    setPadding(px(20), px(12), px(20), px(12))
    isClickable = true; isFocusable = true
    setOnClickListener { onClick() }
}

fun Context.field(hint: String, single: Boolean = true) = EditText(this).apply {
    this.hint = hint
    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
    setTextColor(col(R.color.n_text)); setHintTextColor(col(R.color.n_muted))
    background = rounded(col(R.color.n_card), px(16), px(1), col(R.color.n_line2))
    setPadding(px(14), px(12), px(14), px(12))
    if (single) { isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT }
}

fun Context.card() = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = rounded(col(R.color.n_card), px(16))
    elevation = px(1).toFloat()
    setPadding(px(16), px(14), px(16), px(14))
}

fun LinearLayout.add(v: View, top: Int = 0, width: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
    addView(v, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = context.px(top) })

fun Activity.toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

/** A sheet from the bottom, across the width, with a title and ✕: the PWA's modal on a phone. */
fun Activity.sheet(title: String): Pair<Dialog, LinearLayout> {
    val dialog = Dialog(this).apply { requestWindowFeature(Window.FEATURE_NO_TITLE) }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(px(20), px(8), px(20), px(24)) }
    val head = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(20), px(16), px(12), px(4))
        addView(label(title, 20f, bold = true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(label("✕", 20f, col(R.color.n_muted)).apply {
            setPadding(px(12), px(6), px(12), px(6)); contentDescription = t("editor.cancel"); tag = "sheet-close"
            setOnClickListener { dialog.dismiss() }
        })
    }
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val r = px(28).toFloat()
        background = GradientDrawable().apply { cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f); setColor(col(R.color.n_card)) }
        addView(head)
        addView(ScrollView(this@sheet).apply { addView(body) })
    }
    dialog.setContentView(root)
    dialog.window?.apply {
        setBackgroundDrawable(GradientDrawable().apply { setColor(0) })
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setGravity(Gravity.BOTTOM)
        setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setWindowAnimations(android.R.style.Animation_InputMethod)
    }
    return dialog to body
}

