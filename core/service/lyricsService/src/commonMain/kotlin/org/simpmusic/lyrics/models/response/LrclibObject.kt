package org.simpmusic.lyrics.models.response

import kotlinx.serialization.Serializable

/**
 * One LRCLIB search hit. Any scalar the API can send as explicit null is
 * nullable here: a single null in one entry used to fail the WHOLE batch
 * decode and throw away every result (observed: `"duration":null`).
 */
@Serializable
data class LrclibObject(
    val id: Int? = null,
    val name: String? = null,
    val trackName: String?,
    val artistName: String?,
    val albumName: String?,
    val duration: Float? = null,
    val instrumental: Boolean? = null,
    val plainLyrics: String?,
    val syncedLyrics: String?,
)
