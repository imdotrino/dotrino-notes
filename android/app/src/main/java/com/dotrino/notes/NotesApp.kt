package com.dotrino.notes

import android.content.Context
import com.dotrino.sdk.DotrinoStore
import com.dotrino.sdk.PhoneIdentity
import com.dotrino.sdk.Profile
import com.dotrino.sdk.VaultBackup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * The notes of this process: ONE per app, not per screen (turning the phone must not reopen
 * anything). The store is THIS account's (`notes-<pid>`: another account on the same phone has
 * its own), and when the phone is paired the backup in the vault carries the thread
 * `notes.items` to the PWA and the other devices of the account, and back.
 */
object NotesApp {
    @Volatile var profile: Profile? = null; private set
    private var store: DotrinoStore? = null
    private var backup: VaultBackup? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var loop: Job? = null

    /** The backup's state, for the indicator. Null until the boot says whether there is a vault. */
    @Volatile var sync: SyncState? = null; private set

    /** Called (on any thread) when the notes changed from outside (the vault) or the indicator moved. */
    @Volatile var onChange: (() -> Unit)? = null

    class BootError(message: String, val code: String) : Exception(message)

    private val bootLock = Mutex()
    private val syncLock = Mutex()

    /** Starts (once). Throws [BootError] with a code the screen turns into words. */
    suspend fun boot(context: Context) = bootLock.withLock {
        if (store != null) return@withLock
        val ctx = context.applicationContext
        withContext(Dispatchers.IO) {
            val p = try { PhoneIdentity(ctx).profile() } catch (e: Profile.ProfileError) { throw BootError(e.message ?: e.code, e.code) }
            val account = (p.pid ?: "default").lowercase().replace(Regex("[^a-z0-9-]"), "-")
            val s = DotrinoStore(ctx, "notes-$account")
            s.listThread(NotesLogic.THREAD)   // opens the sealed file now, not on the first tap
            backup = p.vault?.let { VaultBackup(p, s) { k -> k == NotesLogic.THREAD } }
            profile = p; store = s
            sync = SyncState(paired = backup != null)
        }
        if (backup != null) loop = scope.launch { while (true) { syncSoon(0); delay(5 * 60_000) } }
    }

    private fun need(): DotrinoStore = store ?: throw IllegalStateException("notes: not booted")

    fun list(): List<Note> = need().listThread(NotesLogic.THREAD).map(NotesLogic::fromEntry)

    /** Saves (new or changed). Rewriting raises `ts`: otherwise the thread's digest would not change and the vault would not notice. */
    fun save(id: String?, body: String, pinned: Boolean, created: Long?): Note {
        if (body.length > NotesLogic.MAX_BODY) throw BootError("note too long", "note-too-long")
        val now = System.currentTimeMillis()
        val n = Note(id ?: UUID.randomUUID().toString(), body, pinned, created ?: now, now)
        need().appendMessage(NotesLogic.THREAD, NotesLogic.toEntry(n))
        wrote()
        return n
    }

    /** Leaves a tombstone: the note does not come back from the vault or another device. */
    fun remove(id: String) {
        need().removeMessage(NotesLogic.THREAD, id)
        wrote()
    }

    private fun wrote() {
        sync = sync?.let { it.copy(pending = it.pending + 1) }
        onChange?.invoke()
        syncSoon(1_500)
    }

    /** Reconcile with the vault in [wait] ms (a moment after a write, or now). */
    fun syncSoon(wait: Long) {
        if (backup == null) return
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(wait)
            try { syncNow() } catch (e: Exception) { android.util.Log.w("notes", "vault backup", e) }
        }
    }

    /** The code the pillar put on the failure (backup, vault or proxy), so the screen can say it in words. */
    fun codeOf(e: Throwable): String = when (e) {
        is VaultBackup.BackupError -> e.code
        is com.dotrino.sdk.VaultError -> e.code
        is com.dotrino.sdk.ProxyConnection.ProxyError -> e.code
        else -> "unknown"
    }

    /** Reconcile NOW (the «Sync» button). Throws with the backup's code. */
    suspend fun syncNow() = syncLock.withLock {
        val b = backup ?: throw VaultBackup.BackupError("this phone is not paired with a vault", "not-paired")
        val carried = sync?.pending ?: 0
        sync = sync?.copy(syncing = true); onChange?.invoke()
        try {
            val r = withContext(Dispatchers.IO) { b.sync() }
            sync = sync?.let { it.copy(syncing = false, error = null, errorMessage = null, lastSyncAt = System.currentTimeMillis(),
                pending = maxOf(0, it.pending - carried), tooLarge = r.tooLarge.size) }
            onChange?.invoke()
        } catch (e: Exception) {
            sync = sync?.copy(syncing = false, error = codeOf(e), errorMessage = e.message ?: e.toString())
            onChange?.invoke()
            throw e
        }
    }
}
