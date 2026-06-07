package com.example.api

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LeaderboardEntry(
    val username: String,
    val score: Int,
    val maxCube: Int,
    val region: String,
    val timestamp: Long
)

@JsonClass(generateAdapter = true)
data class LeaderboardResponse(
    val key: String,
    val entry: LeaderboardEntry
)
