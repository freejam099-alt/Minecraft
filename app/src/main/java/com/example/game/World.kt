package com.example.game

import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

data class BlockPos(val x: Int, val y: Int, val z: Int)

class World {
    companion object {
        const val WORLD_X = 32
        const val WORLD_Y = 32
        const val WORLD_Z = 16
        const val SEA_LEVEL = 4
    }

    private val blocks = Array(WORLD_X) { Array(WORLD_Y) { ByteArray(WORLD_Z) } }
    private val exposedBlocks = mutableListOf<BlockPos>()

    init {
        generate()
    }

    fun isValid(x: Int, y: Int, z: Int): Boolean {
        return x in 0 until WORLD_X && y in 0 until WORLD_Y && z in 0 until WORLD_Z
    }

    fun getBlock(x: Int, y: Int, z: Int): BlockType {
        if (!isValid(x, y, z)) return BlockType.AIR
        val ordinal = blocks[x][y][z].toInt()
        return BlockType.values().getOrElse(ordinal) { BlockType.AIR }
    }

    fun setBlock(x: Int, y: Int, z: Int, type: BlockType) {
        if (!isValid(x, y, z)) return
        blocks[x][y][z] = type.ordinal.toByte()
        recalculateExposedAround(x, y, z)
    }

    // Get all blocks that should be rendered
    fun getExposedBlocks(): List<BlockPos> {
        return exposedBlocks
    }

    // Dynamic generation of world with hills, trees, water, and ores
    private fun generate() {
        val random = Random(42) // Fixed seed for consistent beautiful world

        // Step 1: Terrain height and base blocks
        for (x in 0 until WORLD_X) {
            for (y in 0 until WORLD_Y) {
                // Generate a rolling landscape using sine and cosine waves
                val freq1 = 0.12f
                val freq2 = 0.25f
                val baseHeight = 6.0f
                val heightOffset = sin(x * freq1) * cos(y * freq1) * 3.5f + sin(x * freq2 + y * freq2) * 1.0f
                val h = (baseHeight + heightOffset).toInt().coerceIn(1, WORLD_Z - 5)

                for (z in 0 until WORLD_Z) {
                    val blockType = when {
                        z > h && z <= SEA_LEVEL -> BlockType.WATER // Fill ocean/lake basins
                        z > h -> BlockType.AIR
                        z == h -> {
                            if (h <= SEA_LEVEL + 1) BlockType.SAND // Beach
                            else BlockType.GRASS
                        }
                        z < h && z >= h - 2 -> {
                            if (h <= SEA_LEVEL + 1) BlockType.SAND
                            else BlockType.DIRT
                        }
                        else -> {
                            // Stone layer - generate ores at depth
                            val randVal = random.nextFloat()
                            when {
                                z <= 2 && randVal < 0.015f -> BlockType.DIAMOND_ORE
                                z <= 5 && randVal < 0.04f -> BlockType.IRON_ORE
                                z <= 8 && randVal < 0.08f -> BlockType.COAL_ORE
                                else -> BlockType.STONE
                            }
                        }
                    }
                    blocks[x][y][z] = blockType.ordinal.toByte()
                }
            }
        }

        // Step 2: Grow trees procedurally
        for (x in 2 until WORLD_X - 2) {
            for (y in 2 until WORLD_Y - 2) {
                val h = getSurfaceHeight(x, y)
                if (h > SEA_LEVEL + 1 && getBlock(x, y, h) == BlockType.GRASS) {
                    // 4% chance to grow a tree in valid spots
                    if (random.nextFloat() < 0.04f) {
                        growTree(x, y, h, random)
                    }
                }
            }
        }

        // Step 3: Calculate initial exposed blocks list
        recalculateAllExposed()
    }

    private fun growTree(x: Int, y: Int, baseHeight: Int, random: Random) {
        // Change grass under trunk to dirt
        blocks[x][y][baseHeight] = BlockType.DIRT.ordinal.toByte()

        // Log height is 3 to 4 blocks
        val trunkHeight = 3 + random.nextInt(2)
        for (z in 1..trunkHeight) {
            val logZ = baseHeight + z
            if (isValid(x, y, logZ)) {
                blocks[x][y][logZ] = BlockType.WOOD_LOG.ordinal.toByte()
            }
        }

        // Create leaves canopy centered at top of the trunk
        val topZ = baseHeight + trunkHeight
        for (lz in topZ - 1..topZ + 1) {
            val radius = if (lz == topZ + 1) 1 else 2
            for (lx in -radius..radius) {
                for (ly in -radius..radius) {
                    val targetX = x + lx
                    val targetY = y + ly
                    if (isValid(targetX, targetY, lz)) {
                        // Skip trunk and don't overwrite wood logs
                        if (getBlock(targetX, targetY, lz) == BlockType.AIR) {
                            // Shave corners of the leaves canopy for a rounder look
                            if (Math.abs(lx) + Math.abs(ly) < radius * 2 || random.nextFloat() < 0.3f) {
                                blocks[targetX][targetY][lz] = BlockType.LEAVES.ordinal.toByte()
                            }
                        }
                    }
                }
            }
        }
    }

    fun getSurfaceHeight(x: Int, y: Int): Int {
        if (x !in 0 until WORLD_X || y !in 0 until WORLD_Y) return 0
        for (z in WORLD_Z - 1 downTo 0) {
            val block = getBlock(x, y, z)
            if (block != BlockType.AIR && block != BlockType.WATER) {
                return z
            }
        }
        return 0
    }

    // Checks if a block position is exposed to air/fluid, meaning it should render
    private fun isBlockPositionExposed(x: Int, y: Int, z: Int): Boolean {
        val block = getBlock(x, y, z)
        if (block == BlockType.AIR) return false

        // Outer boundary is always exposed
        if (x == 0 || x == WORLD_X - 1 || y == 0 || y == WORLD_Y - 1 || z == 0 || z == WORLD_Z - 1) {
            return true
        }

        // Check 6 adjacent directions
        val neighbors = listOf(
            getBlock(x + 1, y, z),
            getBlock(x - 1, y, z),
            getBlock(x, y + 1, z),
            getBlock(x, y - 1, z),
            getBlock(x, y, z + 1),
            getBlock(x, y, z - 1)
        )

        // If any neighbor is transparent or fluid or air, this block's face is visible!
        return neighbors.any { it == BlockType.AIR || it.isTransparent || it.isLiquid }
    }

    private fun recalculateAllExposed() {
        exposedBlocks.clear()
        for (x in 0 until WORLD_X) {
            for (y in 0 until WORLD_Y) {
                for (z in 0 until WORLD_Z) {
                    if (isBlockPositionExposed(x, y, z)) {
                        exposedBlocks.add(BlockPos(x, y, z))
                    }
                }
            }
        }
    }

    private fun recalculateExposedAround(x: Int, y: Int, z: Int) {
        val positionsToCheck = mutableSetOf<BlockPos>()
        // Check current block and all neighbors
        for (dx in -1..1) {
            for (dy in -1..1) {
                for (dz in -1..1) {
                    val px = x + dx
                    val py = y + dy
                    val pz = z + dz
                    if (isValid(px, py, pz)) {
                        positionsToCheck.add(BlockPos(px, py, pz))
                    }
                }
            }
        }

        // Update list
        for (pos in positionsToCheck) {
            val wasInExposed = exposedBlocks.contains(pos)
            val isExposedNow = isBlockPositionExposed(pos.x, pos.y, pos.z)
            if (isExposedNow && !wasInExposed) {
                exposedBlocks.add(pos)
            } else if (!isExposedNow && wasInExposed) {
                exposedBlocks.remove(pos)
            }
        }
    }
}
