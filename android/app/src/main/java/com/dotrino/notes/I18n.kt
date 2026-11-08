package com.dotrino.notes

import android.content.Context
import com.dotrino.sdk.ui.DotrinoLocale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * The app's texts: the SAME as the PWA (src/i18n.js), which `scripts/native-i18n.mjs` flattens
 * to assets/i18n.json (`list.new`). `{name}` is replaced; a missing key or variable is an
 * error, not a hole on screen.
 */
object I18n {
    private var dict: Map<String, Map<String, String>>? = null
    var lang = "es"; private set

    fun load(context: Context) {
        if (dict == null) {
            val text = context.assets.open("i18n.json").use { it.readBytes().toString(Charsets.UTF_8) }
            dict = parse(text)
        }
        lang = DotrinoLocale.current(context)
    }

    /** For the tests: the dictionary without a Context. */
    fun use(d: Map<String, Map<String, String>>, l: String) { dict = d; lang = l }

    fun parse(text: String): Map<String, Map<String, String>> =
        Json.parseToJsonElement(text).jsonObject.mapValues { (_, v) -> v.jsonObject.mapValues { it.value.jsonPrimitive.content } }

    fun has(key: String) = dict?.get(lang)?.containsKey(key) == true

    fun t(key: String, vararg vars: Pair<String, Any>): String {
        val s = dict?.get(lang)?.get(key) ?: throw IllegalStateException("missing i18n key: $key ($lang)")
        if (vars.isEmpty()) return s
        val m = vars.toMap()
        return Regex("\\{(\\w+)\\}").replace(s) { r ->
            (m[r.groupValues[1]] ?: throw IllegalStateException("missing i18n var \"${r.groupValues[1]}\" for $key")).toString()
        }
    }

    private fun locale() = if (lang == "es") Locale.forLanguageTag("es-EC") else Locale.US

    /** The time if it is from today, the day if not (`shortWhen` in the PWA). */
    fun shortWhen(ts: Long): String {
        val d = Date(ts)
        val today = java.util.Calendar.getInstance()
        val c = java.util.Calendar.getInstance().apply { time = d }
        val same = c.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
        return if (same) DateFormat.getTimeInstance(DateFormat.SHORT, locale()).format(d)
        else DateFormat.getDateInstance(DateFormat.MEDIUM, locale()).format(d)
    }

    fun dateTime(ts: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale()).format(Date(ts))
}

fun t(key: String, vararg vars: Pair<String, Any>) = I18n.t(key, *vars)
