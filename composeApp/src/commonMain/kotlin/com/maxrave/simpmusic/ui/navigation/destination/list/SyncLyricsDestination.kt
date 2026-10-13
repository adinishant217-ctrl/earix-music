package com.maxrave.simpmusic.ui.navigation.destination.list

import kotlinx.serialization.Serializable

@Serializable
data class SyncLyricsDestination(
    val videoId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
)
