package com.music.vivi.ui.screens.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

@Composable
fun LibraryScreen(navController: NavController) {
    var showSmartPlaylists by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (showSmartPlaylists) "Smart Playlists" else "Library",
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = { showSmartPlaylists = !showSmartPlaylists }) {
                Text(if (showSmartPlaylists) "Back to library" else "Smart Playlists")
            }
        }
        if (showSmartPlaylists) {
            SmartPlaylistsScreen(onBack = { showSmartPlaylists = false })
        } else {
            LibraryMixScreen(navController)
        }
    }
}
