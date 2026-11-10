package com.music.vivi.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.music.vivi.LocalDatabase
import com.music.vivi.LocalPlayerAwareWindowInsets
import com.music.vivi.LocalPlayerConnection
import com.music.vivi.db.entities.EventWithSong
import com.music.vivi.db.entities.Song
import com.music.vivi.extensions.toMediaItem
import com.music.vivi.playback.queues.ListQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.util.UUID

private enum class SmartRuleKind(val label: String) {
    TITLE("Title contains"),
    ARTIST("Artist contains"),
    ALBUM("Album contains"),
    LIKED("Liked"),
    DOWNLOADED("Downloaded"),
    VIDEO("Video track"),
    PLAY_COUNT_MIN("Minimum play count"),
    ADDED_WITHIN_DAYS("Added within days"),
}

private data class SmartPlaylistRule(
    val kind: SmartRuleKind = SmartRuleKind.TITLE,
    val value: String = "",
)

private data class SmartPlaylistDefinition(
    val id: String,
    val name: String,
    val rules: List<SmartPlaylistRule>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartPlaylistsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val songs by database.allSongs().collectAsState(initial = emptyList())
    val events by database.events().collectAsState(initial = emptyList())
    val playCounts = remember(events) { events.groupingBy(EventWithSong::songId).eachCount() }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            clock = System.currentTimeMillis()
        }
    }

    val playlists = remember {
        mutableStateListOf<SmartPlaylistDefinition>().apply {
            addAll(readSmartPlaylists(context.getSharedPreferences("smart_playlists", 0)))
        }
    }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var playlistName by rememberSaveable { mutableStateOf("") }
    val draftRules = remember { mutableStateListOf<SmartPlaylistRule>() }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    var validationMessage by remember { mutableStateOf<String?>(null) }

    fun openEditor(existing: SmartPlaylistDefinition?) {
        editorId = existing?.id ?: "new"
        playlistName = existing?.name.orEmpty()
        draftRules.clear()
        draftRules.addAll(existing?.rules ?: listOf(SmartPlaylistRule()))
        validationMessage = null
    }

    fun persistPlaylists() {
        context.getSharedPreferences("smart_playlists", 0).edit()
            .putString("definitions", writeSmartPlaylists(playlists))
            .apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = if (editorId == null) onBack else { { editorId = null } }) {
                Text("‹", style = MaterialTheme.typography.headlineMedium)
            }
            Text(
                text = when {
                    editorId != null -> if (editorId == "new") "Create smart playlist" else "Edit smart playlist"
                    else -> "Smart Playlists"
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (editorId == null) {
                Button(onClick = { openEditor(null) }) { Text("Create") }
            } else {
                Button(onClick = {
                    val name = playlistName.trim()
                    val validRules = draftRules.all { rule ->
                        when (rule.kind) {
                            SmartRuleKind.TITLE, SmartRuleKind.ARTIST, SmartRuleKind.ALBUM -> rule.value.isNotBlank()
                            SmartRuleKind.LIKED, SmartRuleKind.DOWNLOADED, SmartRuleKind.VIDEO -> rule.value.toBooleanStrictOrNull() != null
                            SmartRuleKind.PLAY_COUNT_MIN -> rule.value.toIntOrNull()?.let { it >= 0 } == true
                            SmartRuleKind.ADDED_WITHIN_DAYS -> rule.value.toIntOrNull()?.let { it > 0 } == true
                        }
                    }
                    if (name.isBlank() || draftRules.isEmpty() || !validRules) {
                        validationMessage = "Enter a name and valid values for every rule."
                    } else {
                        val updated = SmartPlaylistDefinition(
                            id = editorId!!.takeUnless { it == "new" } ?: UUID.randomUUID().toString(),
                            name = name,
                            rules = draftRules.toList(),
                        )
                        val index = playlists.indexOfFirst { it.id == updated.id }
                        if (index >= 0) playlists[index] = updated else playlists.add(updated)
                        persistPlaylists()
                        editorId = null
                    }
                }) { Text("Save") }
            }
        }

        if (editorId != null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Every rule must match. Results update automatically as your library and listening history change.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                draftRules.forEachIndexed { index, rule ->
                    SmartRuleEditor(
                        rule = rule,
                        onChange = { draftRules[index] = it },
                        onRemove = { if (draftRules.size > 1) draftRules.removeAt(index) },
                    )
                }
                OutlinedButton(onClick = { draftRules.add(SmartPlaylistRule()) }) { Text("Add rule") }
                validationMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            ) {
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            "Create a playlist that stays current using title, artist, album, liked/downloaded status, play count, or when a track was added.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                items(playlists, key = SmartPlaylistDefinition::id) { definition ->
                    val matchingSongs = remember(songs, events, definition, clock) {
                        val now = LocalDateTime.now()
                        songs.filter { song ->
                            definition.rules.all { rule ->
                                matchesSmartRule(song, playCounts[song.id] ?: 0, rule, now)
                            }
                        }
                    }
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            ListItem(
                                headlineContent = { Text(definition.name) },
                                supportingContent = {
                                    Text("${matchingSongs.size} tracks · ${definition.rules.joinToString { it.kind.label }}")
                                },
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { expandedId = if (expandedId == definition.id) null else definition.id }) {
                                    Text(if (expandedId == definition.id) "Hide tracks" else "View tracks")
                                }
                                TextButton(
                                    enabled = matchingSongs.isNotEmpty() && playerConnection != null,
                                    onClick = {
                                        playerConnection?.playQueue(
                                            ListQueue(definition.name, matchingSongs.map(Song::toMediaItem)),
                                        )
                                    },
                                ) { Text("Play all") }
                                TextButton(onClick = { openEditor(definition) }) { Text("Edit") }
                                TextButton(onClick = {
                                    playlists.remove(definition)
                                    if (expandedId == definition.id) expandedId = null
                                    persistPlaylists()
                                }) { Text("Delete") }
                            }
                            if (expandedId == definition.id) {
                                if (matchingSongs.isEmpty()) {
                                    Text("No tracks match these rules.", modifier = Modifier.padding(16.dp))
                                } else {
                                    matchingSongs.forEachIndexed { songIndex, song ->
                                        ListItem(
                                            headlineContent = { Text(song.song.title) },
                                            supportingContent = {
                                                Text(song.artists.joinToString { it.name }.ifBlank { song.song.albumName.orEmpty() })
                                            },
                                            modifier = Modifier.clickable {
                                                playerConnection?.playQueue(
                                                    ListQueue(
                                                        definition.name,
                                                        matchingSongs.map(Song::toMediaItem),
                                                        startIndex = songIndex,
                                                    ),
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmartRuleEditor(
    rule: SmartPlaylistRule,
    onChange: (SmartPlaylistRule) -> Unit,
    onRemove: () -> Unit,
) {
    var showFields by remember { mutableStateOf(false) }
    var showBooleanValues by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showFields = true }, modifier = Modifier.weight(1f)) {
                    Text(rule.kind.label)
                }
                DropdownMenu(expanded = showFields, onDismissRequest = { showFields = false }) {
                    SmartRuleKind.entries.forEach { kind ->
                        DropdownMenuItem(
                            text = { Text(kind.label) },
                            onClick = {
                                showFields = false
                                onChange(
                                    SmartPlaylistRule(
                                        kind,
                                        when (kind) {
                                            SmartRuleKind.LIKED, SmartRuleKind.DOWNLOADED, SmartRuleKind.VIDEO -> "true"
                                            else -> ""
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                TextButton(onClick = onRemove) { Text("Remove") }
            }
            when (rule.kind) {
                SmartRuleKind.LIKED, SmartRuleKind.DOWNLOADED, SmartRuleKind.VIDEO -> {
                    TextButton(onClick = { showBooleanValues = true }) {
                        Text(if (rule.value.toBooleanStrictOrNull() == true) "is true" else "is false")
                    }
                    DropdownMenu(expanded = showBooleanValues, onDismissRequest = { showBooleanValues = false }) {
                        listOf("true", "false").forEach { value ->
                            DropdownMenuItem(
                                text = { Text(if (value == "true") "Yes" else "No") },
                                onClick = { onChange(rule.copy(value = value)); showBooleanValues = false },
                            )
                        }
                    }
                }
                SmartRuleKind.PLAY_COUNT_MIN -> OutlinedTextField(
                    value = rule.value,
                    onValueChange = { onChange(rule.copy(value = it.filter(Char::isDigit))) },
                    label = { Text("At least this many plays") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SmartRuleKind.ADDED_WITHIN_DAYS -> OutlinedTextField(
                    value = rule.value,
                    onValueChange = { onChange(rule.copy(value = it.filter(Char::isDigit))) },
                    label = { Text("Days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                else -> OutlinedTextField(
                    value = rule.value,
                    onValueChange = { onChange(rule.copy(value = it)) },
                    label = { Text("Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun matchesSmartRule(
    song: Song,
    playCount: Int,
    rule: SmartPlaylistRule,
    now: LocalDateTime,
): Boolean {
    val value = rule.value.trim()
    return when (rule.kind) {
        SmartRuleKind.TITLE -> song.song.title.contains(value, ignoreCase = true)
        SmartRuleKind.ARTIST -> song.artists.any { it.name.contains(value, ignoreCase = true) }
        SmartRuleKind.ALBUM -> song.song.albumName.orEmpty().contains(value, ignoreCase = true)
        SmartRuleKind.LIKED -> song.song.liked == (value.toBooleanStrictOrNull() ?: true)
        SmartRuleKind.DOWNLOADED -> song.song.isDownloaded == (value.toBooleanStrictOrNull() ?: true)
        SmartRuleKind.VIDEO -> song.song.isVideo == (value.toBooleanStrictOrNull() ?: true)
        SmartRuleKind.PLAY_COUNT_MIN -> playCount >= (value.toIntOrNull() ?: Int.MAX_VALUE)
        SmartRuleKind.ADDED_WITHIN_DAYS -> {
            val days = value.toLongOrNull() ?: return false
            val addedAt = song.song.inLibrary ?: song.song.dateDownload ?: song.song.likedDate ?: return false
            !addedAt.isBefore(now.minusDays(days)) && !addedAt.isAfter(now)
        }
    }
}

private fun readSmartPlaylists(preferences: android.content.SharedPreferences): List<SmartPlaylistDefinition> =
    runCatching {
        val encoded = preferences.getString("definitions", "[]") ?: "[]"
        val array = JSONArray(encoded)
        List(array.length()) { index ->
            val item = array.getJSONObject(index)
            val rulesArray = item.optJSONArray("rules") ?: JSONArray()
            val rules = List(rulesArray.length()) { ruleIndex ->
                val rule = rulesArray.getJSONObject(ruleIndex)
                SmartPlaylistRule(
                    kind = SmartRuleKind.entries.firstOrNull { it.name == rule.optString("kind") } ?: SmartRuleKind.TITLE,
                    value = rule.optString("value"),
                )
            }
            SmartPlaylistDefinition(item.optString("id", UUID.randomUUID().toString()), item.optString("name"), rules)
        }
    }.getOrDefault(emptyList())

private fun writeSmartPlaylists(playlists: List<SmartPlaylistDefinition>): String =
    JSONArray().apply {
        playlists.forEach { playlist ->
            put(
                JSONObject().apply {
                    put("id", playlist.id)
                    put("name", playlist.name)
                    put("rules", JSONArray().apply {
                        playlist.rules.forEach { rule ->
                            put(JSONObject().apply {
                                put("kind", rule.kind.name)
                                put("value", rule.value)
                            })
                        }
                    })
                },
            )
        }
    }.toString()
