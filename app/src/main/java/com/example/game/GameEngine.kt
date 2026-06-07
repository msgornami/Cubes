package com.example.game

import androidx.compose.ui.graphics.Color
import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

class GameEngine {
    // Map dimensions
    val mapWidth = 2200f
    val mapHeight = 2200f

    // Standard configurations
    private val maxFloatingCubes = 160
    private val baseSpeed = 4.8f
    private val boostSpeedMultiplier = 1.8f
    private val nodeSpacing = 35f // Pixels between adjacent cubes in chain

    // Real-time state
    var player: GamePlayer = createDefaultPlayer("You")
    val opponents = mutableListOf<GamePlayer>()
    val floatingCubes = mutableListOf<FloatingCube>()
    val killEvents = mutableListOf<KillEvent>()
    
    var isRunning = false
    var totalTimeLimitSeconds = 120 // Timer per session if desired, or endless. We will use endless survival with session counters
    var startTimeMs = 0L
    var currentScore = 0

    // Typical bot names representing an online lobby
    private val botNames = listOf(
        "Neon_Slayer", "Hyper_Merge", "AlleyCat", "Shervin_007", "PixelMaster",
        "Rambo2048", "CubeDrifter", "PersianKing", "Zero_Cool", "SpeedyGamer",
        "BlockCrusher", "MatrixBit", "Sven_CPU", "ByteSize", "NinjaBlock",
        "ApexCubes", "GigaStack", "MegaMerge", "CrownHolder", "GoldDigger"
    )

    fun startNewGame(playerName: String) {
        val finalName = if (playerName.trim().isEmpty()) "Player_${Random.nextInt(100, 999)}" else playerName
        player = createDefaultPlayer(finalName)
        opponents.clear()
        floatingCubes.clear()
        killEvents.clear()
        currentScore = player.score

        // Seed opponents
        val usedNames = mutableSetOf<String>()
        val startOpponentCount = 14
        for (i in 0 until startOpponentCount) {
            val botName = getUniqueBotName(usedNames)
            opponents.add(spawnRandomBot(botName))
        }

        // Seed initial food
        replenishFloatingCubes()
        startTimeMs = System.currentTimeMillis()
        isRunning = true
    }

    private fun createDefaultPlayer(name: String): GamePlayer {
        val px = mapWidth / 2f
        val py = mapHeight / 2f
        
        val player = GamePlayer(
            id = "PLAYER_ID",
            name = name,
            isBot = false,
            angle = -PI.toFloat() / 2f
        )
        // Add default head cube (value 2)
        player.cubeChain.add(CubeNode(px, py, 2))
        return player
    }

    private fun spawnRandomBot(name: String): GamePlayer {
        val botX = Random.nextFloat() * (mapWidth - 200f) + 100f
        val botY = Random.nextFloat() * (mapHeight - 200f) + 100f
        
        val bot = GamePlayer(
            id = UUID.randomUUID().toString(),
            name = name,
            isBot = true,
            angle = Random.nextFloat() * (2 * PI.toFloat())
        )
        
        // Bots can start with random size to make the landscape interesting (values 2, 4, 8, 16, 32)
        val initialValues = listOf(2, 4, 8, 16, 32)
        val startBase = initialValues[Random.nextInt(initialValues.size)]
        bot.cubeChain.add(CubeNode(botX, botY, startBase))
        
        // Add 1 or 2 small tail items to make them snakes too
        val tailCount = Random.nextInt(1, 4)
        var currentTailVal = startBase
        for (j in 0 until tailCount) {
            val tailDiv = listOf(2, 4).random()
            currentTailVal = max(2, currentTailVal / tailDiv)
            val bx = botX - cos(bot.angle) * nodeSpacing * (j + 1)
            val by = botY - sin(bot.angle) * nodeSpacing * (j + 1)
            bot.cubeChain.add(CubeNode(bx, by, currentTailVal))
        }
        
        return bot
    }

    private fun getUniqueBotName(used: MutableSet<String>): String {
        var name = botNames.random()
        var counter = 1
        while (used.contains(name) && counter < 100) {
            name = botNames.random() + "_" + Random.nextInt(10, 99)
            counter++
        }
        used.add(name)
        return name
    }

    // Tick update (typically called at ~60Hz)
    fun gameTick(joystickX: Float, joystickY: Float) {
        if (!isRunning) return

        // 1. Update player navigation direction
        if (player.isAlive) {
            if (abs(joystickX) > 0.05f || abs(joystickY) > 0.05f) {
                // Steer player based on drag vector
                player.angle = atan2(joystickY, joystickX)
            }
            updatePlayerPosition(player)
        }

        // 2. Update bot navigation
        opponents.forEach { bot ->
            if (bot.isAlive) {
                steeringAI(bot)
                updatePlayerPosition(bot)
            }
        }

        // 3. Keep opponents count stable (respawn dead ones after a while)
        if (opponents.count { it.isAlive } < 12) {
            val usedNames = opponents.map { it.name }.toMutableSet()
            opponents.add(spawnRandomBot(getUniqueBotName(usedNames)))
        }

        // 4. Resolve float cube collections
        checkCubeCollections()

        // 5. Resolve player-to-player hit combats
        checkPlayerCollisions()

        // 6. Slowly spawn ambient food
        replenishFloatingCubes()
    }

    private fun updatePlayerPosition(gamePlayer: GamePlayer) {
        if (gamePlayer.cubeChain.isEmpty()) return

        // Calculate speed
        var finalSpeed = baseSpeed
        if (gamePlayer.isBoosting && gamePlayer.score > 16) {
            finalSpeed *= boostSpeedMultiplier
            
            // Drip trailing blocks as sweat debris (boost penalty)
            val now = System.currentTimeMillis()
            if (now - gamePlayer.lastTimeBoosted > 600) {
                gamePlayer.lastTimeBoosted = now
                shrinkTailForBoost(gamePlayer)
            }
        }

        // Move the head cube
        val head = gamePlayer.cubeChain[0]
        val dx = cos(gamePlayer.angle) * finalSpeed
        val dy = sin(gamePlayer.angle) * finalSpeed
        
        head.x += dx
        head.y += dy

        // Keep inside boundaries smoothly bounce/bound
        head.x = head.x.coerceIn(20f, mapWidth - 20f)
        head.y = head.y.coerceIn(20f, mapHeight - 20f)

        // Trailing blocks follow the predecessor with spring dampening
        for (i in 1 until gamePlayer.cubeChain.size) {
            val prev = gamePlayer.cubeChain[i - 1]
            val curr = gamePlayer.cubeChain[i]

            val gapX = prev.x - curr.x
            val gapY = prev.y - curr.y
            val dist = sqrt(gapX * gapX + gapY * gapY)

            if (dist > nodeSpacing) {
                // Desired coordinate
                val targetX = prev.x - (gapX / dist) * nodeSpacing
                val targetY = prev.y - (gapY / dist) * nodeSpacing
                
                // Linear lerp interpolation for that fluid curved trailing look
                curr.x += (targetX - curr.x) * 0.45f
                curr.y += (targetY - curr.y) * 0.45f
            }
        }
    }

    // Steering logic for bots
    private fun steeringAI(bot: GamePlayer) {
        val head = bot.cubeChain.getOrNull(0) ?: return
        
        // Find nearest items in visual spectrum
        var nearestFood: FloatingCube? = null
        var minFoodDist = 320f
        
        floatingCubes.forEach { cube ->
            val dist = calculateDistance(head.x, head.y, cube.x, cube.y)
            if (dist < minFoodDist) {
                minFoodDist = dist
                nearestFood = cube
            }
        }

        // Scan players for predator (eat smaller, run from larger)
        var threatPlayer: GamePlayer? = null
        var preyPlayer: GamePlayer? = null
        var minThreatDist = 280f
        var minPreyDist = 280f

        val candidates = mutableListOf<GamePlayer>().apply {
            if (player.isAlive) add(player)
            addAll(opponents.filter { it.isAlive && it.id != bot.id })
        }

        candidates.forEach { other ->
            val otherHead = other.cubeChain.getOrNull(0) ?: return@forEach
            val dist = calculateDistance(head.x, head.y, otherHead.x, otherHead.y)
            
            if (dist < 300f) {
                if (other.cubeChain[0].value > head.value) {
                    if (dist < minThreatDist) {
                        minThreatDist = dist
                        threatPlayer = other
                    }
                } else if (other.cubeChain[0].value < head.value) {
                    if (dist < minPreyDist) {
                        minPreyDist = dist
                        preyPlayer = other
                    }
                }
            }
        }

        // Action Decision Tree
        var targetAngle = bot.angle
        
        if (threatPlayer != null) {
            // Run Away! Steer 180 degrees away from the threat
            val threatHead = threatPlayer!!.cubeChain[0]
            val pathAngle = atan2(threatHead.y - head.y, threatHead.x - head.x)
            targetAngle = pathAngle + PI.toFloat() // Opposite angle
            
            // Randomly use boost to escape!
            if (Random.nextFloat() < 0.15f && bot.score > 24) {
                bot.isBoosting = true
            } else {
                bot.isBoosting = false
            }
        } else if (preyPlayer != null) {
            // Chase!
            val preyHead = preyPlayer!!.cubeChain[0]
            targetAngle = atan2(preyHead.y - head.y, preyHead.x - head.x)
            
            // Boost to chase!
            if (Random.nextFloat() < 0.25f && bot.score > 24) {
                bot.isBoosting = true
            }
        } else if (nearestFood != null) {
            // Move toward food
            targetAngle = atan2(nearestFood!!.y - head.y, nearestFood!!.x - head.x)
            bot.isBoosting = false
        } else {
            // Idle drift
            if (Random.nextFloat() < 0.015f) {
                targetAngle += (Random.nextFloat() * 1.5f - 0.75f)
            }
            bot.isBoosting = false
        }

        // Apply smooth angle interpolation (prevents twitchy bot turns)
        val angleDiff = filterAngleDifference(targetAngle, bot.angle)
        bot.angle += angleDiff * 0.15f
    }

    private fun checkCubeCollections() {
        // Collect cubes for all players (User + Bots)
        val activePlayers = mutableListOf<GamePlayer>().apply {
            if (player.isAlive) add(player)
            addAll(opponents.filter { it.isAlive })
        }

        val collectedIndices = mutableSetOf<String>()

        activePlayers.forEach { p ->
            val head = p.cubeChain.getOrNull(0) ?: return@forEach
            floatingCubes.forEach { cube ->
                if (!collectedIndices.contains(cube.id)) {
                    val dist = calculateDistance(head.x, head.y, cube.x, cube.y)
                    if (dist < 42f) {
                        collectedIndices.add(cube.id)
                        
                        // Collect cube
                        resolveCubeCollection(p, cube.value)
                    }
                }
            }
        }

        // Remove eaten cubes
        floatingCubes.removeAll { collectedIndices.contains(it.id) }
    }

    private fun resolveCubeCollection(p: GamePlayer, cubeValue: Int) {
        val head = p.cubeChain[0]

        // Core 2048 snake mechanic:
        // 1. If Collected cube matches head -> Double the head!
        if (head.value == cubeValue) {
            head.value *= 2
            triggerKillFeedEvent(KillEvent(UUID.randomUUID().toString(), p.name, "Mega Merge", head.value))
        } else {
            // 2. Otherwise append to the tail
            p.cubeChain.add(CubeNode(p.cubeChain.last().x, p.cubeChain.last().y, cubeValue))
        }

        // 3. Resolve internal tail merges (if two adjacent blocks have the same value, merge them!)
        resolveChainMerges(p.cubeChain)
        
        if (!p.isBot) {
            currentScore = p.score
        }
    }

    private fun resolveChainMerges(chain: MutableList<CubeNode>) {
        if (chain.size <= 1) return
        
        var mergedThisCycle: Boolean
        do {
            mergedThisCycle = false
            var i = 1
            while (i < chain.size) {
                val prev = chain[i - 1]
                val curr = chain[i]
                
                if (prev.value == curr.value) {
                    // Merge them into prev
                    prev.value *= 2
                    
                    // Remove current node
                    chain.removeAt(i)
                    mergedThisCycle = true
                    // Do not increment i, scan again from this index
                } else {
                    i++
                }
            }
        } while (mergedThisCycle && chain.size > 1)
    }

    private fun shrinkTailForBoost(gamePlayer: GamePlayer) {
        if (gamePlayer.cubeChain.size <= 1) return
        
        // Remove the smallest/last cube of the tail and disperse it on the ground
        val lastNode = gamePlayer.cubeChain.removeLast()
        
        // Pop as debris floating on board behind player
        val sweatOffsetAngle = gamePlayer.angle + PI.toFloat() // spawn behind
        val sx = lastNode.x + cos(sweatOffsetAngle) * 55f
        val sy = lastNode.y + sin(sweatOffsetAngle) * 55f
        
        val poppedValue = lastNode.value
        floatingCubes.add(
            FloatingCube(
                id = UUID.randomUUID().toString(),
                x = sx.coerceIn(20f, mapWidth - 20f),
                y = sy.coerceIn(20f, mapHeight - 20f),
                value = poppedValue,
                color = lastNode.getThemeColor(),
                isDebris = true
            )
        )
        
        if (!gamePlayer.isBot) {
            currentScore = gamePlayer.score
        }
    }

    private fun checkPlayerCollisions() {
        val alivePlayers = mutableListOf<GamePlayer>().apply {
            if (player.isAlive) add(player)
            addAll(opponents.filter { it.isAlive })
        }

        // Compare each player head to all other players body & head
        for (i in 0 until alivePlayers.size) {
            val p1 = alivePlayers[i]
            if (!p1.isAlive) continue // Skip if p1 was already killed in this tick
            val head1 = p1.cubeChain.getOrNull(0) ?: continue

            for (j in 0 until alivePlayers.size) {
                if (i == j) continue
                val p2 = alivePlayers[j]
                if (!p2.isAlive) continue // Skip if p2 was already killed

                // Copy to list to avoid ConcurrentModificationException since killPlayer clears the chain list
                val p2ChainSnapshot = p2.cubeChain.toList()
                for (index in p2ChainSnapshot.indices) {
                    if (!p1.isAlive || !p2.isAlive) break // Stop checking checking if either player is already dead
                    
                    val node2 = p2ChainSnapshot[index]
                    val dist = calculateDistance(head1.x, head1.y, node2.x, node2.y)
                    
                    if (dist < 46f) {
                        // Collision!
                        if (index == 0) {
                            // Head-on collision: larger head eats smaller head!
                            if (head1.value > node2.value) {
                                killPlayer(p2, p1)
                            } else if (node2.value > head1.value) {
                                killPlayer(p1, p2)
                            }
                        } else {
                            // Body crash: If p1 hits body of p2, compare head sizes
                            val head2 = p2.cubeChain.getOrNull(0) ?: break
                            if (head1.value > head2.value) {
                                // Cut body! p1 destroys p2!
                                killPlayer(p2, p1)
                            } else {
                                // p1 dies!
                                killPlayer(p1, p2)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun killPlayer(victim: GamePlayer, killer: GamePlayer) {
        if (!victim.isAlive) return
        victim.isAlive = false
        killer.killCount++

        // Create kill feed event
        val event = KillEvent(
            id = UUID.randomUUID().toString(),
            killerName = killer.name,
            victimName = victim.name,
            killerValue = killer.cubeChain[0].value
        )
        triggerKillFeedEvent(event)

        // Drop scattered loot nearby
        victim.cubeChain.forEach { node ->
            // Disperse cubes in circle
            val angle = Random.nextFloat() * (2 * PI.toFloat())
            val scatterDist = Random.nextFloat() * 110f + 30f
            val lx = (node.x + cos(angle) * scatterDist).coerceIn(20f, mapWidth - 20f)
            val ly = (node.y + sin(angle) * scatterDist).coerceIn(20f, mapHeight - 20f)
            
            // Value is subdivided slightly for loot balance
            val lootVal = if (node.value > 2) listOf(2, 4, node.value / 2).random() else 2
            
            floatingCubes.add(
                FloatingCube(
                    id = UUID.randomUUID().toString(),
                    x = lx,
                    y = ly,
                    value = lootVal,
                    color = node.getThemeColor(),
                    isDebris = true
                )
            )
        }

        victim.cubeChain.clear()

        if (!victim.isBot) {
            isRunning = false // Stop game on user loss
        }
    }

    private fun triggerKillFeedEvent(event: KillEvent) {
        killEvents.add(0, event)
        if (killEvents.size > 6) {
            killEvents.removeLast()
        }
    }

    private fun replenishFloatingCubes() {
        if (floatingCubes.size >= maxFloatingCubes) return

        val spawnCount = maxFloatingCubes - floatingCubes.size
        // Typical starting loot probability standard cubes (2, 4, 8, 16)
        val spawnValues = listOf(2, 2, 2, 2, 4, 4, 4, 8, 8, 16)

        for (i in 0 until spawnCount) {
            val fx = Random.nextFloat() * (mapWidth - 60f) + 30f
            val fy = Random.nextFloat() * (mapHeight - 60f) + 30f
            val fv = spawnValues.random()
            
            val tempNode = CubeNode(0f, 0f, fv)
            
            floatingCubes.add(
                FloatingCube(
                    id = UUID.randomUUID().toString(),
                    x = fx,
                    y = fy,
                    value = fv,
                    color = tempNode.getThemeColor()
                )
            )
        }
    }

    // Mathematical utility helpers
    private fun calculateDistance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        return sqrt(dx * dx + dy * dy)
    }

    private fun filterAngleDifference(target: Float, current: Float): Float {
        var diff = target - current
        while (diff < -PI.toFloat()) diff += 2 * PI.toFloat()
        while (diff > PI.toFloat()) diff -= 2 * PI.toFloat()
        return diff
    }
}
