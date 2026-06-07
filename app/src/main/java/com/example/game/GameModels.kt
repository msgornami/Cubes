package com.example.game

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.*

// Single Cube node in a player's trailing chains
data class CubeNode(
    var x: Float,
    var y: Float,
    var value: Int,
    var targetX: Float = x,
    var targetY: Float = y
) {
    // Helper to get color representing this value
    fun getThemeColor(): Color {
        return when (value) {
            2 -> CubeColor2
            4 -> CubeColor4
            8 -> CubeColor8
            16 -> CubeColor16
            32 -> CubeColor32
            64 -> CubeColor64
            128 -> CubeColor128
            256 -> CubeColor256
            512 -> CubeColor512
            1024 -> CubeColor1024
            2048 -> CubeColor2048
            else -> CubeColor4096
        }
    }
}

// Participant in the match (Can be the user or an online-style bot)
data class GamePlayer(
    val id: String,
    val name: String,
    val isBot: Boolean,
    val cubeChain: MutableList<CubeNode> = mutableListOf(),
    var isAlive: Boolean = true,
    var angle: Float = 0f, // Direction of motion in radians
    var isBoosting: Boolean = false, // Speed boost state
    var killCount: Int = 0,
    var lastTimeBoosted: Long = 0
) {
    // Total sum of all chain nodes
    val score: Int
        get() = cubeChain.sumOf { it.value }

    // Maximum value achieved in the snake chain
    val maxCube: Int
        get() = cubeChain.maxOfOrNull { it.value } ?: 2
}

// Natural cubes floating in space
data class FloatingCube(
    val id: String,
    var x: Float,
    var y: Float,
    val value: Int,
    val color: Color,
    val isDebris: Boolean = false // If created by a dead player
)

// Represents key events on the battlefield
data class KillEvent(
    val id: String,
    val killerName: String,
    val victimName: String,
    val killerValue: Int,
    val timestamp: Long = System.currentTimeMillis()
)
