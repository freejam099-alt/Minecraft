package com.example.game

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

enum class GameItem(
    val id: String,
    val displayName: String,
    val iconEmoji: String,
    val isBlock: Boolean,
    val blockType: BlockType? = null,
    val attackDamage: Float = 1.0f,
    val miningSpeedMultiplier: Float = 1.0f,
    val isFood: Boolean = false
) {
    // Blocks as items
    GRASS_ITEM("grass", "Grass Block", "🟩", true, BlockType.GRASS),
    DIRT_ITEM("dirt", "Dirt Block", "🟫", true, BlockType.DIRT),
    STONE_ITEM("stone", "Stone Block", "🪨", true, BlockType.STONE),
    COBBLESTONE_ITEM("cobblestone", "Cobblestone", "🧱", true, BlockType.COBBLESTONE),
    WOOD_LOG_ITEM("wood_log", "Oak Log", "🪵", true, BlockType.WOOD_LOG),
    OAK_PLANKS_ITEM("oak_planks", "Oak Planks", "🪵", true, BlockType.OAK_PLANKS),
    LEAVES_ITEM("leaves", "Leaves", "🍃", true, BlockType.LEAVES),
    SAND_ITEM("sand", "Sand", "⏳", true, BlockType.SAND),
    CRAFTING_TABLE_ITEM("crafting_table", "Crafting Table", "📦", true, BlockType.CRAFTING_TABLE),

    // Smelting / Resource items
    COAL("coal", "Coal", "⬛", false),
    IRON_ORE_ITEM("iron_ore", "Iron Ore", "🟫", true, BlockType.IRON_ORE),
    IRON_INGOT("iron_ingot", "Iron Ingot", "🪙", false),
    DIAMOND("diamond", "Diamond", "💎", false),
    STICK("stick", "Stick", "🥢", false),
    APPLE("apple", "Apple", "🍎", false, isFood = true),

    // Tools and weapons
    WOODEN_SWORD("wooden_sword", "Wooden Sword", "🗡️", false, attackDamage = 4.0f),
    WOODEN_PICKAXE("wooden_pickaxe", "Wooden Pickaxe", "🪵", false, miningSpeedMultiplier = 2.0f),
    STONE_SWORD("stone_sword", "Stone Sword", "🗡️", false, attackDamage = 5.0f),
    STONE_PICKAXE("stone_pickaxe", "Stone Pickaxe", "⛏️", false, miningSpeedMultiplier = 4.0f),
    IRON_SWORD("iron_sword", "Iron Sword", "⚔️", false, attackDamage = 6.0f),
    IRON_PICKAXE("iron_pickaxe", "Iron Pickaxe", "⛏️", false, miningSpeedMultiplier = 6.0f),
    DIAMOND_SWORD("diamond_sword", "Diamond Sword", "⚔️", false, attackDamage = 7.0f),
    DIAMOND_PICKAXE("diamond_pickaxe", "Diamond Pickaxe", "⛏️", false, miningSpeedMultiplier = 8.0f);

    companion object {
        fun fromBlockType(type: BlockType): GameItem? {
            return values().find { it.blockType == type }
        }
    }
}

class Player {
    var x = 16.0f
    var y = 16.0f
    var z = 10.0f

    var vx = 0.0f
    var vy = 0.0f
    var vz = 0.0f

    var yaw = 0.0f // horizontal angle in radians
    var pitch = 0.0f // vertical angle in radians

    var health = 20.0f // 10 hearts
    val maxHealth = 20.0f
    var hunger = 20.0f // 10 shanks
    val maxHunger = 20.0f

    var onGround = false

    // Quick inventory mapping item to quantity
    val inventory = mutableMapOf<GameItem, Int>()
    
    // Bottom hotbar (5 slots)
    val hotbar = Array<GameItem?>(5) { null }
    var selectedHotbarSlot = 0

    init {
        // Initial items for testing and survival start
        inventory[GameItem.APPLE] = 5
        inventory[GameItem.OAK_PLANKS_ITEM] = 4
        inventory[GameItem.STICK] = 4

        hotbar[0] = GameItem.APPLE
        hotbar[1] = GameItem.OAK_PLANKS_ITEM
    }

    fun getSelectedItem(): GameItem? {
        return hotbar[selectedHotbarSlot]
    }

    fun addItem(item: GameItem, count: Int = 1) {
        val currentCount = inventory.getOrDefault(item, 0)
        inventory[item] = currentCount + count

        // Auto-populate hotbar if there's an empty slot and item is not already in it
        if (!hotbar.contains(item)) {
            val emptySlot = hotbar.indexOf(null)
            if (emptySlot != -1) {
                hotbar[emptySlot] = item
            }
        }
    }

    fun removeItem(item: GameItem, count: Int = 1): Boolean {
        val currentCount = inventory.getOrDefault(item, 0)
        if (currentCount < count) return false
        
        val newCount = currentCount - count
        if (newCount == 0) {
            inventory.remove(item)
            // Remove from hotbar too if fully depleted
            val index = hotbar.indexOf(item)
            if (index != -1) {
                hotbar[index] = null
            }
        } else {
            inventory[item] = newCount
        }
        return true
    }

    // Move player based on inputs and apply collision logic against the world
    fun update(inputX: Float, inputY: Float, world: World, jumpRequested: Boolean) {
        // Natural hunger drain
        hunger = (hunger - 0.001f).coerceIn(0.0f, maxHunger)
        if (hunger <= 0.0f) {
            // Starvation damage
            health = (health - 0.01f).coerceIn(0.0f, maxHealth)
        } else if (hunger > 17.0f && health < maxHealth) {
            // Natural regeneration when full
            health = (health + 0.005f).coerceIn(0.0f, maxHealth)
        }

        // 1. Calculate movement vectors relative to looking angle (yaw)
        val speed = 0.12f
        if (inputX != 0.0f || inputY != 0.0f) {
            // Convert input into world coordinates based on yaw
            val moveX = inputY * sin(yaw) + inputX * cos(yaw)
            val moveY = inputY * cos(yaw) - inputX * sin(yaw)
            
            vx = moveX * speed
            vy = moveY * speed
        } else {
            // Decay horizontal velocity
            vx *= 0.6f
            vy *= 0.6f
        }

        // 2. Apply gravity
        vz -= 0.015f // gravity step
        vz = vz.coerceIn(-0.5f, 0.5f) // terminal velocity

        // 3. Jump logic
        if (jumpRequested && onGround) {
            vz = 0.22f
            onGround = false
        }

        // 4. Update coordinates with basic collision detection
        val nextX = x + vx
        val nextY = y + vy
        val nextZ = z + vz

        // Perform X collision
        if (canMoveTo(nextX, y, z, world)) {
            x = nextX
        } else {
            vx = 0.0f // stop moving on X
        }

        // Perform Y collision
        if (canMoveTo(x, nextY, z, world)) {
            y = nextY
        } else {
            vy = 0.0f // stop moving on Y
        }

        // Perform Z collision
        if (vz < 0) {
            // Falling down - check if on ground
            if (canMoveTo(x, y, nextZ, world)) {
                z = nextZ
                onGround = false
            } else {
                // Landed! Check if we took fall damage
                val fallDistance = -vz
                if (fallDistance > 0.32f) {
                    val damage = (fallDistance * 25f - 5.0f).coerceIn(0.0f, maxHealth)
                    if (damage > 0) {
                        health = (health - damage).coerceIn(0.0f, maxHealth)
                    }
                }
                z = floor(z) // Snap to solid block height
                vz = 0.0f
                onGround = true
            }
        } else {
            // Moving up - check ceiling
            if (canMoveTo(x, y, nextZ, world)) {
                z = nextZ
                onGround = false
            } else {
                vz = 0.0f // hit ceiling
            }
        }

        // Void bounds check
        if (z < -8.0f) {
            health = (health - 4.0f).coerceIn(0.0f, maxHealth)
            respawn(world)
        }
    }

    fun respawn(world: World) {
        x = 16.0f
        y = 16.0f
        val surf = world.getSurfaceHeight(16, 16)
        z = (surf + 2).toFloat()
        vx = 0.0f
        vy = 0.0f
        vz = 0.0f
        if (health <= 0.0f) {
            health = maxHealth
            hunger = maxHunger
        }
    }

    // Bounding box collision check
    private fun canMoveTo(tx: Float, ty: Float, tz: Float, world: World): Boolean {
        // Player height is ~1.8 blocks, width is ~0.6 blocks
        val r = 0.3f // bounding radius
        val h = 1.8f // height

        val minX = floor(tx - r).toInt()
        val maxX = floor(tx + r).toInt()
        val minY = floor(ty - r).toInt()
        val maxY = floor(ty + r).toInt()
        val minZ = floor(tz).toInt()
        val maxZ = floor(tz + h).toInt()

        for (bx in minX..maxX) {
            for (by in minY..maxY) {
                for (bz in minZ..maxZ) {
                    if (world.isValid(bx, by, bz)) {
                        val blockType = world.getBlock(bx, by, bz)
                        if (blockType.isSolid) {
                            return false // overlapping with a solid block!
                        }
                    }
                }
            }
        }
        return true
    }
}
