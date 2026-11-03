package com.projectyaya.tvchannel

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.watchStore by preferencesDataStore("watch")
private val ID = stringPreferencesKey("last_id")
private val NAME = stringPreferencesKey("last_name")
private val SECTION = stringPreferencesKey("last_section")

/** The channel watched last, and the list section it was opened from (zapping stays inside that section). */
data class LastWatched(val id: String, val name: String, val section: String)

/** Null when nothing was saved yet or the store cannot be read. Only the channel is stored, never a location. */
suspend fun readLastWatched(context: Context): LastWatched? = runCatching {
    val p = context.watchStore.data.first()
    val id = p[ID] ?: return@runCatching null
    LastWatched(id, p[NAME].orEmpty(), p[SECTION].orEmpty())
}.getOrNull()

suspend fun saveLastWatched(context: Context, last: LastWatched) {
    runCatching {
        context.watchStore.edit {
            it[ID] = last.id
            it[NAME] = last.name
            it[SECTION] = last.section
        }
    }
}

/**
 * The channel to resume: the same id, else the same name (antenna channel ids change after a rescan, their
 * display names do not). Null when it is gone, e.g. removed from the curated list.
 */
fun resolveResume(last: LastWatched?, channels: List<Channel>): Channel? {
    if (last == null) return null
    val playable = channels.filter { it.launch.isEmpty() }
    return playable.firstOrNull { it.id == last.id } ?: playable.firstOrNull { last.name.isNotEmpty() && it.name == last.name }
}
