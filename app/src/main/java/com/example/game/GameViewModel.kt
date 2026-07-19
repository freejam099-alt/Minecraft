package com.example.game

import android.app.Application
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

data class RaycastResult(
    val hitPos: BlockPos,
    val placePos: BlockPos,
    val distance: Float
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    val world = World()
    val player = Player()
    
    private val _mobs = MutableStateFlow<List<Mob>>(emptyList())
    val mobs: StateFlow<List<Mob>> = _mobs.asStateFlow()

    private val _timeOfDay = MutableStateFlow(6000L) // Start at noon (6000 ticks)
    val timeOfDay: StateFlow<Long> = _timeOfDay.asStateFlow()

    private val _isInventoryOpen = MutableStateFlow(false)
    val isInventoryOpen: StateFlow<Boolean> = _isInventoryOpen.asStateFlow()

    private val _nearCraftingTable = MutableStateFlow(false)
    val nearCraftingTable: StateFlow<Boolean> = _nearCraftingTable.asStateFlow()

    val zombieHitTimestamps = mutableStateMapOf<String, Long>()
    
    // Procedural Sound Synth Manager
    val soundEffects = SoundEffects()

    // Virtual Joysticks Inputs
    var movementJoystickX = 0f
    var movementJoystickY = 0f
    var cameraLookX = 0f
    var cameraLookY = 0f
    var jumpRequested = false

    private var gameLoopJob: Job? = null

    init {
        // Spawn 3 hostile Zombies around the player at a safe distance initially
        val initialMobs = listOf(
            Mob("zombie_1", 8f, 16f, 0f),
            Mob("zombie_2", 24f, 8f, 0f),
            Mob("zombie_3", 16f, 24f, 0f)
        )
        // Adjust spawn heights to match surface
        initialMobs.forEach { mob ->
            val surf = world.getSurfaceHeight(mob.x.toInt(), mob.y.toInt())
            mob.z = (surf + 1).toFloat()
        }
        _mobs.value = initialMobs

        // Respawn player at world center surface level
        player.respawn(world)

        startGameLoop()
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (true) {
                // 1. Advance Day/Night Cycle (1 tick per 16ms, 24000 ticks = 24000 * 16ms = 384 seconds per full day)
                _timeOfDay.value = (_timeOfDay.value + 4) % 24000

                // 2. Rotate Camera using swipe/joystick offsets
                if (cameraLookX != 0f || cameraLookY != 0f) {
                    player.yaw = (player.yaw + cameraLookX * 0.005f) % (2 * Math.PI.toFloat())
                    player.pitch = (player.pitch + cameraLookY * 0.005f).coerceIn(-1.4f, 1.4f)
                    
                    // Slowly damp look vector
                    cameraLookX *= 0.7f
                    cameraLookY *= 0.7f
                    if (Math.abs(cameraLookX) < 0.01f) cameraLookX = 0f
                    if (Math.abs(cameraLookY) < 0.01f) cameraLookY = 0f
                }

                // 3. Update Player physical state
                player.update(movementJoystickX, movementJoystickY, world, jumpRequested)
                jumpRequested = false // reset jump request flag

                // 4. Update Mobs AI and states
                val currentMobs = _mobs.value
                for (mob in currentMobs) {
                    mob.update(player, world, soundEffects)
                }

                // 5. Check if standing near a Crafting Table block
                checkCraftingTableAdjacency()

                // Trigger UI update
                _mobs.value = emptyList() // force state emission
                _mobs.value = currentMobs

                delay(16L) // ~60 FPS update
            }
        }
    }

    private fun checkCraftingTableAdjacency() {
        val px = floor(player.x).toInt()
        val py = floor(player.y).toInt()
        val pz = floor(player.z).toInt()

        // Scan 3x3x3 blocks around the player for a Crafting Table
        var found = false
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -2..2) {
                    if (world.getBlock(px + dx, py + dy, pz + dz) == BlockType.CRAFTING_TABLE) {
                        found = true
                        break
                    }
                }
                if (found) break
            }
            if (found) break
        }
        _nearCraftingTable.value = found
    }

    // Advanced 3D Raycasting to detect block aimed at
    fun raycastLookAt(maxReach: Float = 4.5f): RaycastResult? {
        // Calculate player camera looking unit vector
        val cosP = cos(player.pitch)
        val sinP = sin(player.pitch)
        val cosY = cos(player.yaw)
        val sinY = sin(player.yaw)

        // Coordinate directions match: DX = cosP * sinY, DY = cosP * cosY, DZ = sinP
        val dx = cosP * sinY
        val dy = cosP * cosY
        val dz = sinP

        var rx = player.x
        var ry = player.y
        // camera eye level is player.z + 1.62
        var rz = player.z + 1.62f

        val step = 0.05f
        val stepsCount = (maxReach / step).toInt()

        var prevBlockPos = BlockPos(floor(rx).toInt(), floor(ry).toInt(), floor(rz).toInt())

        for (i in 0 until stepsCount) {
            rx += dx * step
            ry += dy * step
            rz += dz * step

            val bx = floor(rx).toInt()
            val by = floor(ry).toInt()
            val bz = floor(rz).toInt()

            val currentBlockPos = BlockPos(bx, by, bz)

            if (world.isValid(bx, by, bz)) {
                val blockType = world.getBlock(bx, by, bz)
                if (blockType != BlockType.AIR && blockType != BlockType.WATER) {
                    // Hit solid block!
                    return RaycastResult(
                        hitPos = currentBlockPos,
                        placePos = prevBlockPos,
                        distance = i * step
                    )
                }
            }
            prevBlockPos = currentBlockPos
        }
        return null
    }

    // Trigger action when tapping the big action button
    fun performAction() {
        val activeItem = player.getSelectedItem()

        // 1. Attack Mob check - check if a Zombie is within reach in front of the player
        val hitMob = getMobInLookCone(reach = 3.5f)
        if (hitMob != null) {
            val damage = activeItem?.attackDamage ?: 1.0f
            hitMob.takeDamage(damage, player.yaw, soundEffects)
            zombieHitTimestamps[hitMob.id] = System.currentTimeMillis()

            if (hitMob.health <= 0f) {
                // Respawn zombie elsewhere and drop loot
                hitMob.respawnFarFrom(player, world)
                
                // Reward player with random loot directly in inventory
                val lootRandom = Math.random()
                when {
                    lootRandom < 0.35f -> {
                        player.addItem(GameItem.APPLE, 1)
                    }
                    lootRandom < 0.70f -> {
                        player.addItem(GameItem.WOOD_LOG_ITEM, 1)
                    }
                    else -> {
                        player.addItem(GameItem.COBBLESTONE_ITEM, 1)
                    }
                }
            }
            return
        }

        // 2. Break or Place block depending on equipped item
        val raycast = raycastLookAt()
        if (raycast != null) {
            if (activeItem != null && activeItem.isBlock) {
                // PLACE BLOCK
                val target = raycast.placePos
                val blockToPlace = activeItem.blockType ?: BlockType.AIR
                
                // Ensure block doesn't overlap player's personal bounding box
                val pr = 0.35f // player radius
                val ph = 1.8f  // player height
                val pxMin = player.x - pr
                val pxMax = player.x + pr
                val pyMin = player.y - pr
                val pyMax = player.y + pr
                val pzMin = player.z
                val pzMax = player.z + ph

                val overlapX = target.x >= floor(pxMin).toInt() && target.x <= floor(pxMax).toInt()
                val overlapY = target.y >= floor(pyMin).toInt() && target.y <= floor(pyMax).toInt()
                val overlapZ = target.z >= floor(pzMin).toInt() && target.z <= floor(pzMax).toInt()

                if (!(overlapX && overlapY && overlapZ) && world.isValid(target.x, target.y, target.z)) {
                    // Place it!
                    world.setBlock(target.x, target.y, target.z, blockToPlace)
                    player.removeItem(activeItem, 1)
                    soundEffects.playPlace()
                }
            } else {
                // MINE / BREAK BLOCK
                val target = raycast.hitPos
                val blockType = world.getBlock(target.x, target.y, target.z)
                
                // Check tools and mining requirements
                val canMine = blockType.requiredTool == "none" || 
                        (activeItem != null && activeItem.miningSpeedMultiplier > 1.0f) // has some pickaxe

                if (canMine) {
                    // Mine success - clear block and add drop
                    world.setBlock(target.x, target.y, target.z, BlockType.AIR)
                    soundEffects.playMine()

                    // Convert mined block to item drop
                    val dropItem = when (blockType) {
                        BlockType.GRASS -> GameItem.DIRT_ITEM
                        BlockType.DIRT -> GameItem.DIRT_ITEM
                        BlockType.STONE -> GameItem.COBBLESTONE_ITEM
                        BlockType.COAL_ORE -> GameItem.COAL
                        BlockType.IRON_ORE -> GameItem.IRON_ORE_ITEM
                        BlockType.DIAMOND_ORE -> GameItem.DIAMOND
                        else -> GameItem.fromBlockType(blockType)
                    }

                    if (dropItem != null) {
                        player.addItem(dropItem, 1)
                    }
                } else {
                    // Can't mine stone without a pickaxe!
                    soundEffects.playClick()
                }
            }
        } else {
            // Swing at air
            soundEffects.playJump()
        }
    }

    private fun getMobInLookCone(reach: Float): Mob? {
        for (mob in _mobs.value) {
            if (mob.health <= 0) continue
            // Vector from player camera eye to mob center
            val dx = mob.x - player.x
            val dy = mob.y - player.y
            val dz = (mob.z + 0.9f) - (player.z + 1.62f)
            val dist = sqrt(dx * dx + dy * dy + dz * dz)

            if (dist < reach) {
                // Angle checks - verify if zombie center is aligned with look vector
                val cosP = cos(player.pitch)
                val sinP = sin(player.pitch)
                val cosY = cos(player.yaw)
                val sinY = sin(player.yaw)

                val lookDx = cosP * sinY
                val lookDy = cosP * cosY
                val lookDz = sinP

                // Dot product of look vector and vector to mob
                val dot = (dx * lookDx + dy * lookDy + dz * lookDz) / dist
                // If dot product > 0.88, it is within ~25 degrees cone of center crosshair!
                if (dot > 0.88f) {
                    return mob
                }
            }
        }
        return null
    }

    // Eat active item if food
    fun eatFood() {
        val activeItem = player.getSelectedItem()
        if (activeItem != null && activeItem.isFood) {
            player.removeItem(activeItem, 1)
            player.hunger = (player.hunger + 6.0f).coerceAtMost(player.maxHunger)
            player.health = (player.health + 4.0f).coerceAtMost(player.maxHealth)
            soundEffects.playCraft()
        }
    }

    fun craftItem(recipe: CraftingRecipe) {
        val success = CraftingManager.craft(recipe, player, _nearCraftingTable.value)
        if (success) {
            soundEffects.playCraft()
        } else {
            soundEffects.playClick()
        }
    }

    fun toggleInventory() {
        _isInventoryOpen.value = !_isInventoryOpen.value
        soundEffects.playClick()
    }

    fun selectHotbarSlot(index: Int) {
        if (index in 0 until 5) {
            player.selectedHotbarSlot = index
            soundEffects.playClick()
        }
    }

    fun equipItemToHotbar(item: GameItem, slotIndex: Int) {
        if (slotIndex in 0 until 5) {
            // Swap items if already present
            player.hotbar[slotIndex] = item
            soundEffects.playClick()
        }
    }

    override fun onCleared() {
        gameLoopJob?.cancel()
        super.onCleared()
    }
}
