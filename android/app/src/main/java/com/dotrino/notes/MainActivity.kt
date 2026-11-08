package com.dotrino.notes

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dotrino.sdk.ui.DotrinoLocale
import com.dotrino.sdk.ui.DotrinoTopbar
import com.dotrino.sdk.ui.DotrinoWebActivity
import com.dotrino.sdk.ui.IdentityRequired
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The app, the same as the PWA: the panel with the notes (create, search, open, pin, delete, and
 * whether they are in the vault) and the editor, which only writes. On a phone they are two
 * screens («back» from the editor returns to the panel); on a wide screen, side by side.
 * Plain native views (CONVENCIONES §16.2).
 */
class MainActivity : Activity() {
    /** The note being written. `id` null = new and not saved yet (an empty new note is never saved). */
    private class Editor(val key: String, var id: String?, var body: String, var pinned: Boolean, var created: Long, var dirty: Boolean = false)

    private val ui = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = Dispatchers.IO.limitedParallelism(1)   // the store's writes, one after the other
    private val saveLock = Mutex()

    private var booted = false
    private var notes: List<Note> = emptyList()
    private var editor: Editor? = null
    private var query = ""
    private var syncOpen = false
    private var syncBusy = false
    private var refreshQueued = false

    private var content: FrameLayout? = null
    private var listPane: LinearLayout? = null
    private var editorPane: FrameLayout? = null
    private var syncHost: LinearLayout? = null
    private var itemsHost: LinearLayout? = null
    private var shownEditorKey: String? = null
    private var statusLabel: TextView? = null
    private var tooLongLabel: TextView? = null
    private var bodyField: EditText? = null
    private val autosave = Runnable { scope.launch { flush() } }

    private val wide get() = resources.configuration.screenWidthDp >= 720

    override fun attachBaseContext(base: Context) = super.attachBaseContext(DotrinoLocale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        I18n.load(this)
        setContentView(shell())
        boot()
    }

    override fun onResume() { super.onResume(); if (booted) NotesApp.syncSoon(0) }
    override fun onPause() { scope.launch { flush() }; super.onPause() }
    override fun onDestroy() { NotesApp.onChange = null; scope.cancel(); super.onDestroy() }

    @Deprecated("Activity without AndroidX: below Android 13 the back button still comes here")
    override fun onBackPressed() {
        if (!wide && editor != null) { closeEditor(); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    /**
     * From Android 13 «back» (button or gesture) goes through the dispatcher, and with targetSdk 36
     * `onBackPressed` is no longer called: the callback is registered only while the editor is
     * open on a phone, so that «back» from the panel still leaves the app.
     */
    private val backToPanel: Any? = if (android.os.Build.VERSION.SDK_INT >= 33)
        android.window.OnBackInvokedCallback { closeEditor() } else null
    private var backRegistered = false

    private fun syncBack() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val want = !wide && editor != null
        if (want == backRegistered) return
        val cb = backToPanel as android.window.OnBackInvokedCallback
        if (want) onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        else onBackInvokedDispatcher.unregisterOnBackInvokedCallback(cb)
        backRegistered = want
    }

    // ---------- start ----------

    private fun boot() {
        if (!IdentityRequired.check(this)) { showProblem(t("native.noIdentityApp")); return }
        showMessage(t("boot.loading"))
        scope.launch {
            try {
                NotesApp.boot(this@MainActivity)
                booted = true
                notes = NotesApp.list()
                // On a wide screen the first note opens, as in the PWA; on a phone, the panel first.
                if (wide) NotesLogic.sort(notes).firstOrNull()?.let { editor = editorFor(it) }
                NotesApp.onChange = { refreshSoon() }
                setContentView(shell())   // the bar again, now with the profile
                layoutPanes()
            } catch (e: NotesApp.BootError) {
                when (e.code) {
                    "no-identity-app" -> { IdentityRequired.show(this@MainActivity); showProblem(t("native.noIdentityApp")) }
                    "no-profile", "no-profile-keys" -> showProblem(t("native.noProfile"), profileActions = true)
                    else -> showProblem(t("errors.unknown", "message" to (e.message ?: e.code)))
                }
            } catch (e: Exception) { showProblem(t("errors.unknown", "message" to (e.message ?: e.toString()))) }
        }
    }

    private fun showMessage(msg: String) {
        val c = content ?: return
        c.removeAllViews()
        c.addView(label(msg, 16f, col(R.color.n_muted)).apply { gravity = Gravity.CENTER },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun showProblem(msg: String, profileActions: Boolean = false) {
        val c = content ?: return
        c.removeAllViews()
        c.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(32), px(32), px(32), px(32))
            add(label(msg, 16f, col(R.color.n_muted)).apply { gravity = Gravity.CENTER; tag = "boot-error" })
            if (profileActions) {
                add(pill(t("native.createProfile"), filled = true) { DotrinoWebActivity.open(this@MainActivity, DotrinoTopbar.CREATE_URL) }
                    .apply { tag = "create-profile" }, top = 20, width = ViewGroup.LayoutParams.WRAP_CONTENT)
                add(pill(t("native.adoptProfile")) { DotrinoWebActivity.open(this@MainActivity, DotrinoTopbar.ADOPT_URL) }
                    .apply { tag = "adopt-profile" }, top = 12, width = ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            add(pill(t("boot.retry"), filled = !profileActions) { boot() }.apply { tag = "boot-retry" }, top = 20, width = ViewGroup.LayoutParams.WRAP_CONTENT)
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    // ---------- the frame ----------

    private fun shell(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(col(R.color.n_bg))
        fitsSystemWindows = true
        addView(DotrinoTopbar(this@MainActivity, repo = "imdotrino/dotrino-notes",
            brand = DotrinoTopbar.Brand("Notes", R.drawable.notes_brand),
            profile = NotesApp.profile?.topbar(),
        ) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DotrinoTopbar.HOME))) }.view)
        content = FrameLayout(this@MainActivity)
        addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        listPane = null; editorPane = null; shownEditorKey = null
    }

    /** The two panes, built once: the list and the editor repaint inside them without losing focus. */
    private fun layoutPanes() {
        val c = content ?: return
        c.removeAllViews()
        val list = listPane()
        val ed = FrameLayout(this).apply { tag = "editor" }
        listPane = list; editorPane = ed
        if (wide) {
            c.addView(LinearLayout(this).apply {
                addView(list, LinearLayout.LayoutParams(px(320), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(View(this@MainActivity).apply { setBackgroundColor(col(R.color.n_line)) }, LinearLayout.LayoutParams(px(1), ViewGroup.LayoutParams.MATCH_PARENT))
                addView(ed, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val full = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            c.addView(list, full); c.addView(ed, FrameLayout.LayoutParams(full))
        }
        renderList(); renderEditor()
    }

    private fun refreshSoon() = ui.post {
        if (refreshQueued) return@post
        refreshQueued = true
        ui.postDelayed({ refreshQueued = false; refresh() }, 60)
    }

    /** The notes again from the store (a sync brought something, or the indicator moved). */
    private fun refresh() {
        if (!booted || isFinishing) return
        notes = NotesApp.list()
        editor?.let { ed ->
            val id = ed.id ?: return@let
            val incoming = notes.firstOrNull { it.id == id }
            when (NotesLogic.mergeOpen(ed.dirty, incoming)) {
                "replace" -> if (incoming != null && (incoming.body != ed.body || incoming.pinned != ed.pinned)) {
                    ed.body = incoming.body; ed.pinned = incoming.pinned
                    shownEditorKey = null   // repaint the editor with what arrived
                }
                "close" -> { editor = null }
            }
        }
        renderList(); renderEditor()
    }

    // ---------- the panel ----------

    private fun listPane(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        tag = "sidebar"
        setBackgroundColor(col(R.color.n_bg))
        val top = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(px(14), px(12), px(14), px(4)) }
        top.add(label(t("list.title"), 20f, bold = true))
        val sync = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; tag = "sync" }
        syncHost = sync
        top.add(sync, top = 10)
        top.add(pill("+ " + t("list.new"), filled = true) { newNote() }.apply { tag = "new-note" }, top = 12)
        top.add(field(t("list.search")).apply {
            tag = "search"; contentDescription = t("list.search")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(query)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; renderItems() }
            })
        }, top = 10)
        addView(top)
        val items = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(px(10), px(6), px(10), px(24)) }
        itemsHost = items
        addView(ScrollView(this@MainActivity).apply { addView(items) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private fun renderList() { renderSync(); renderItems() }

    private fun renderItems() {
        val host = itemsHost ?: return
        host.removeAllViews()
        val visible = NotesLogic.sort(NotesLogic.filter(notes, query))
        when {
            notes.isEmpty() -> host.add(label(t("list.empty"), 14f, col(R.color.n_muted)).apply { gravity = Gravity.CENTER; tag = "list-empty"; setPadding(px(12), px(24), px(12), 0) })
            visible.isEmpty() -> host.add(label(t("list.noResults"), 14f, col(R.color.n_muted)).apply { gravity = Gravity.CENTER; setPadding(px(12), px(24), px(12), 0) })
            else -> for (n in visible) host.add(itemRow(n), top = 6)
        }
    }

    private fun itemRow(n: Note): View = LinearLayout(this).apply {
        tag = "note-item:" + n.id
        gravity = Gravity.CENTER_VERTICAL
        val active = editor?.id == n.id
        background = rounded(col(if (active) R.color.n_accent_soft else R.color.n_card), px(16), px(1), col(if (active) R.color.n_accent else R.color.n_line))
        setPadding(px(14), px(10), px(6), px(10))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            setOnClickListener { scope.launch { openNote(n) } }
            add(label(NotesLogic.titleOf(n.body).ifEmpty { t("list.untitled") }, 15f, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            NotesLogic.previewOf(n.body).takeIf { it.isNotEmpty() }?.let {
                add(label(it, 13f, col(R.color.n_muted)).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, top = 2)
            }
            add(label(I18n.shortWhen(n.ts), 12f, col(R.color.n_muted)), top = 4)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(iconButton("📌", if (n.pinned) t("editor.unpin") else t("editor.pin"), "note-pin", on = n.pinned) { scope.launch { togglePin(n.id) } })
        addView(iconButton("🗑", t("editor.remove"), "note-remove") { confirmRemove(n) })
    }

    private fun iconButton(icon: String, desc: String, tag: String, on: Boolean = false, run: () -> Unit) = label(icon, 17f).apply {
        this.tag = tag; contentDescription = desc
        gravity = Gravity.CENTER
        alpha = if (tag == "note-pin" && !on) 0.35f else 1f
        setPadding(px(10), px(8), px(10), px(8))
        isClickable = true
        setOnClickListener { run() }
    }

    // ---------- the indicator ----------

    private fun renderSync() {
        val host = syncHost ?: return
        host.removeAllViews()
        val s = NotesApp.sync
        val light = NotesLogic.light(s)
        host.tag = "sync:$light"
        host.background = rounded(col(R.color.n_card), px(16), px(1), col(R.color.n_line))
        host.setPadding(px(12), px(10), px(10), px(10))
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val state = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL; tag = "sync-state"; isClickable = true
            setOnClickListener { syncOpen = !syncOpen; renderSync() }
            addView(View(this@MainActivity).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(col(when (light) { "green" -> R.color.n_green; "yellow" -> R.color.n_yellow; else -> R.color.n_line2 })) }
            }, LinearLayout.LayoutParams(px(12), px(12)).apply { marginEnd = px(8) })
            val headline = if (s?.syncing == true || syncBusy) t("sync.busy") else t("sync.$light")
            addView(label(headline, 13f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        row.addView(state, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // Without a paired vault there is nobody to sync with: the button is there, but off.
        val canSync = s != null && s.paired && !s.syncing && !syncBusy
        row.addView(label(if (s?.syncing == true || syncBusy) t("sync.busy") else t("sync.button"), 13f, col(R.color.n_text), bold = true).apply {
            tag = "sync-now"
            background = rounded(col(R.color.n_card2), px(16), px(1), col(R.color.n_line2))
            setPadding(px(12), px(6), px(12), px(6))
            isEnabled = canSync; alpha = if (canSync) 1f else 0.45f
            setOnClickListener { if (canSync) syncNow() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(8) })
        host.add(row)
        val reason = reasonOf(s)
        if (reason != null) host.add(label(reason, 12f, col(R.color.n_muted)).apply { tag = "sync-reason" }, top = 6)
        if (syncOpen && s != null) {
            if (s.pending > 0) host.add(label(t("sync.pending", "count" to s.pending), 12f, col(R.color.n_muted)).apply { tag = "sync-pending" }, top = 4)
            if (s.tooLarge > 0) host.add(label(t("sync.tooLarge", "count" to s.tooLarge), 12f, col(R.color.n_danger)), top = 4)
            s.lastSyncAt?.let { host.add(label(t("sync.lastSync", "when" to I18n.dateTime(it)), 12f, col(R.color.n_muted)), top = 4) }
        }
        if (s != null && !s.paired) {
            host.add(label(t("sync.howToLink"), 12f, col(R.color.n_accent), bold = true).apply {
                tag = "sync-howto"; isClickable = true
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wiki.dotrino.com/" + (if (I18n.lang == "es") "" else "en/") + "vault/emparejar/"))) }
            }, top = 6)
        }
    }

    private fun reasonOf(s: SyncState?): String? = when {
        s == null -> null
        !s.paired -> t("sync.off.not-paired")
        s.error != null -> errorText(s.error, s.errorMessage)
        else -> null
    }

    private fun errorText(code: String, message: String?): String =
        if (code != "other" && I18n.has("sync.errors.$code")) t("sync.errors.$code") else t("sync.errors.other", "message" to (message ?: code))

    private fun syncNow() {
        syncBusy = true; renderSync()
        scope.launch {
            try { flush(); NotesApp.syncNow(); toast(t("sync.done")) }
            catch (e: Exception) { toast(errorText(NotesApp.codeOf(e), e.message ?: e.toString())) }
            finally { syncBusy = false; refresh() }
        }
    }

    // ---------- the editor ----------

    private fun editorFor(n: Note) = Editor(n.id, n.id, n.body, n.pinned, n.created)

    private fun newNote() {
        scope.launch {
            flush()
            editor = Editor(java.util.UUID.randomUUID().toString(), null, "", false, System.currentTimeMillis())
            renderList(); renderEditor()
            bodyField?.let { f -> f.requestFocus(); getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(f, 0) }
        }
    }

    private suspend fun openNote(n: Note) {
        flush()
        editor = editorFor(notes.firstOrNull { it.id == n.id } ?: n)
        renderList(); renderEditor()
    }

    private fun closeEditor() {
        scope.launch { flush(); editor = null; hideKeyboard(); renderList(); renderEditor() }
    }

    private fun hideKeyboard() {
        bodyField?.let { getSystemService(android.view.inputmethod.InputMethodManager::class.java).hideSoftInputFromWindow(it.windowToken, 0) }
    }

    private fun renderEditor() {
        val pane = editorPane ?: return
        val ed = editor
        syncBack()
        listPane?.visibility = if (!wide && ed != null) View.GONE else View.VISIBLE
        pane.visibility = if (!wide && ed == null) View.GONE else View.VISIBLE
        val key = ed?.key ?: "empty"
        if (key == shownEditorKey) { renderStatus(); return }
        shownEditorKey = key
        pane.removeAllViews()
        statusLabel = null; tooLongLabel = null; bodyField = null
        val full = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        if (ed == null) {
            pane.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(px(32), px(32), px(32), px(32)); tag = "editor-empty"
                add(label(if (notes.isNotEmpty()) t("editor.pick") else t("list.empty"), 15f, col(R.color.n_muted)).apply { gravity = Gravity.CENTER })
                add(pill("+ " + t("list.new"), filled = true) { newNote() }.apply { tag = "empty-new-note" }, top = 16, width = ViewGroup.LayoutParams.WRAP_CONTENT)
            }, full)
            return
        }
        pane.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(12), px(8), px(12), px(12))
            val bar = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = px(40) }
            if (!wide) bar.addView(label("‹", 28f, col(R.color.n_accent)).apply {
                tag = "editor-back"; contentDescription = t("native.back")
                setPadding(px(6), 0, px(14), px(2)); isClickable = true
                setOnClickListener { closeEditor() }
            })
            statusLabel = label("", 13f, col(R.color.n_muted)).apply { tag = "editor-status" }
            bar.addView(statusLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            add(bar)
            val field = EditText(this@MainActivity).apply {
                tag = "editor-body"; contentDescription = t("editor.placeholder")
                hint = t("editor.placeholder")
                setText(ed.body)
                gravity = Gravity.TOP or Gravity.START
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 17f)
                setLineSpacing(0f, 1.25f)
                setTextColor(col(R.color.n_text)); setHintTextColor(col(R.color.n_muted))
                background = rounded(col(R.color.n_card), px(16), px(1), col(R.color.n_line))
                setPadding(px(16), px(14), px(16), px(14))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                isSingleLine = false
                addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(s: Editable?) { editBody(s?.toString() ?: "") }
                })
            }
            bodyField = field
            addView(field, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = px(6) })
            tooLongLabel = label("", 13f, col(R.color.n_danger)).apply { visibility = View.GONE; setPadding(px(4), px(8), px(4), 0) }
            add(tooLongLabel!!)
        }, full)
        renderStatus()
    }

    private fun renderStatus() {
        val ed = editor ?: return
        val saved = notes.firstOrNull { it.id == ed.id }
        statusLabel?.text = when {
            ed.dirty -> t("editor.saving")
            saved != null -> t("editor.edited", "when" to I18n.shortWhen(saved.ts)) + if (ed.pinned) "  ·  📌 " + t("list.pinned") else ""
            else -> ""
        }
        tooLongLabel?.apply {
            val over = ed.body.length > NotesLogic.MAX_BODY
            visibility = if (over) View.VISIBLE else View.GONE
            if (over) text = t("editor.tooLong", "count" to ed.body.length, "max" to NotesLogic.MAX_BODY)
        }
    }

    private fun editBody(body: String) {
        val ed = editor ?: return
        if (body == ed.body) return
        ed.body = body; ed.dirty = true
        renderStatus()
        ui.removeCallbacks(autosave)
        ui.postDelayed(autosave, 800)
    }

    /** Saves the editor if it has changes. Two saves never overlap: the second waits for the first. */
    private suspend fun flush() {
        ui.removeCallbacks(autosave)
        saveLock.withLock {
            val ed = editor ?: return
            if (!ed.dirty) return
            // A new note left empty does not exist yet: nothing is saved.
            if (ed.id == null && ed.body.isBlank()) { ed.dirty = false; return }
            if (ed.body.length > NotesLogic.MAX_BODY) return   // the warning is on screen; nothing is cut
            val body = ed.body
            try {
                val n = withContext(io) { NotesApp.save(ed.id, body, ed.pinned, ed.created) }
                ed.id = n.id; ed.created = n.created
                // If the writing went on while saving, it stays dirty.
                ed.dirty = ed.body != body
                if (ed.dirty) ui.postDelayed(autosave, 800)
            } catch (e: Exception) {
                android.util.Log.w("notes", "save", e)
                toast(t("errors.unknown", "message" to (e.message ?: e.toString())))
            }
        }
        if (booted) { notes = NotesApp.list(); renderItems(); renderStatus() }
    }

    /** Pin or unpin. If it is the open note, it goes with the rest of its changes. */
    private suspend fun togglePin(id: String) {
        val ed = editor
        if (ed != null && ed.id == id) {
            ed.pinned = !ed.pinned; ed.dirty = true
            flush()
        } else {
            val n = notes.firstOrNull { it.id == id } ?: return
            withContext(io) { NotesApp.save(n.id, n.body, !n.pinned, n.created) }
            notes = NotesApp.list()
        }
        renderItems(); renderStatus()
    }

    private fun confirmRemove(n: Note) {
        val (dialog, body) = sheet(t("editor.removeTitle"))
        body.add(label(NotesLogic.titleOf(n.body).ifEmpty { t("list.untitled") }, 16f, bold = true))
        body.add(label(t("editor.removeBody"), 15f, col(R.color.n_muted)), top = 8)
        body.add(LinearLayout(this).apply {
            gravity = Gravity.END
            addView(pill(t("editor.cancel")) { dialog.dismiss() }.apply { tag = "remove-cancel" })
            addView(pill(t("editor.removeConfirm"), filled = true) { dialog.dismiss(); scope.launch { remove(n.id) } }.apply {
                tag = "remove-confirm"; background = rounded(col(R.color.n_danger), px(24))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = px(10) })
        }, top = 20)
        dialog.show()
    }

    /** Deletes (with a tombstone). If it was the open one, the next one opens (on a phone, back to the panel). */
    private suspend fun remove(id: String) {
        val isOpen = editor?.id == id
        if (isOpen) { ui.removeCallbacks(autosave); editor?.dirty = false }
        try {
            withContext(io) { NotesApp.remove(id) }
            notes = NotesApp.list()
            if (isOpen) editor = if (wide) NotesLogic.sort(notes).firstOrNull()?.let(::editorFor) else null
            toast(t("editor.removed"))
        } catch (e: Exception) { toast(t("errors.unknown", "message" to (e.message ?: e.toString()))) }
        renderList(); renderEditor()
    }
}
