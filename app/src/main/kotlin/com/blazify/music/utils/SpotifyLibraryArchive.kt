package com.blazify.music.utils

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.util.zip.ZipInputStream

/** Parses Spotify's official account-data ZIP locally. No Spotify login or sync is used. */
object SpotifyLibraryArchive {
    data class Collection(val name: String, val tracks: List<SpotifyPlaylist.Track>)

    private val json = Json { ignoreUnknownKeys = true }

    fun read(context: Context, uri: Uri): Result<List<Collection>> = runCatching {
        val files = linkedMapOf<String, String>()
        val stream = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el archivo")
        ZipInputStream(stream.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && name.endsWith(".json", true) &&
                    (name.equals("YourLibrary.json", true) || name.matches(Regex("Playlist.*\\.json", RegexOption.IGNORE_CASE)))
                ) files[name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
            }
        }
        parse(files)
    }

    fun parse(files: Map<String, String>): List<Collection> {
        val result = mutableListOf<Collection>()
        files.forEach { (fileName, raw) ->
            val root = json.parseToJsonElement(raw)
            if (fileName.equals("YourLibrary.json", true)) {
                val liked = findTrackObjects(root).mapNotNull { it.toTrack() }
                if (liked.isNotEmpty()) result += Collection("Liked Songs", liked.distinctBy { it.title to it.artists })
            } else if (fileName.matches(Regex("Playlist.*\\.json", RegexOption.IGNORE_CASE))) {
                collectPlaylists(root, result)
            }
        }
        return result
    }

    private fun collectPlaylists(element: JsonElement, output: MutableList<Collection>) {
        when (element) {
            is JsonArray -> element.forEach { collectPlaylists(it, output) }
            is JsonObject -> {
                val playlistName = element.text("name", "playlistName")
                val items = element["items"] ?: element["tracks"]
                if (playlistName != null && items != null) {
                    val tracks = findTrackObjects(items).mapNotNull { it.toTrack() }
                    if (tracks.isNotEmpty()) output += Collection(playlistName, tracks)
                } else {
                    element.values.forEach { collectPlaylists(it, output) }
                }
            }
            else -> Unit
        }
    }

    private fun findTrackObjects(element: JsonElement): List<JsonObject> {
        val found = mutableListOf<JsonObject>()
        fun visit(node: JsonElement) {
            when (node) {
                is JsonArray -> node.forEach(::visit)
                is JsonObject -> {
                    val nestedTrack = node["track"]
                    if (nestedTrack is JsonObject) visit(nestedTrack)
                    else if (node.text("trackName", "track_name", "track") != null &&
                        node.text("artistName", "artist_name", "artist") != null
                    ) found += node
                    else node.values.forEach(::visit)
                }
                else -> Unit
            }
        }
        visit(element)
        return found
    }

    private fun JsonObject.toTrack(): SpotifyPlaylist.Track? {
        val title = text("trackName", "track_name", "track", "name", "title") ?: return null
        val artists = text("artistName", "artist_name", "artist")
            ?: (this["artists"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.joinToString(", ")
            ?: ""
        val durationMs = text("duration_ms", "durationMs")?.toLongOrNull() ?: 0L
        return SpotifyPlaylist.Track(title.trim(), artists.trim(), (durationMs / 1000).toInt())
    }

    private fun JsonObject.text(vararg keys: String): String? {
        keys.forEach { key ->
            val value = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()
            if (!value.isNullOrBlank() && value != "null") return value
        }
        return null
    }
}
