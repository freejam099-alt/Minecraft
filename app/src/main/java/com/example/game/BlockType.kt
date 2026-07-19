package com.example.game

import androidx.compose.ui.graphics.Color

enum class BlockType(
    val id: String,
    val displayName: String,
    val isSolid: Boolean,
    val isTransparent: Boolean = false,
    val isLiquid: Boolean = false,
    val hardness: Float = 1.0f, // Time to break in seconds
    val requiredTool: String = "none" // "none", "pickaxe"
) {
    AIR("air", "Air", false, isTransparent = true),
    GRASS("grass", "Grass", true, hardness = 0.6f),
    DIRT("dirt", "Dirt", true, hardness = 0.5f),
    STONE("stone", "Stone", true, hardness = 1.5f, requiredTool = "pickaxe"),
    COBBLESTONE("cobblestone", "Cobblestone", true, hardness = 1.2f, requiredTool = "pickaxe"),
    WOOD_LOG("wood_log", "Oak Log", true, hardness = 1.0f),
    OAK_PLANKS("oak_planks", "Oak Planks", true, hardness = 0.8f),
    LEAVES("leaves", "Oak Leaves", true, isTransparent = true, hardness = 0.2f),
    SAND("sand", "Sand", true, hardness = 0.4f),
    WATER("water", "Water", false, isTransparent = true, isLiquid = true, hardness = 99f),
    COAL_ORE("coal_ore", "Coal Ore", true, hardness = 1.8f, requiredTool = "pickaxe"),
    IRON_ORE("iron_ore", "Iron Ore", true, hardness = 2.0f, requiredTool = "pickaxe"),
    DIAMOND_ORE("diamond_ore", "Diamond Ore", true, hardness = 3.0f, requiredTool = "pickaxe"),
    CRAFTING_TABLE("crafting_table", "Crafting Table", true, hardness = 1.0f);

    // Get color for a specific face
    // 0: TOP, 1: BOTTOM, 2: NORTH, 3: SOUTH, 4: EAST, 5: WEST
    fun getFaceColor(face: Int): Color {
        return when (this) {
            AIR -> Color.Transparent
            GRASS -> {
                if (face == 0) Color(0xFF4CAF50) // Bright grass green
                else if (face == 1) Color(0xFF8B5A2B) // Brown dirt
                else Color(0xFF795548) // Grass-dirt side mix
            }
            DIRT -> Color(0xFF8B5A2B)
            STONE -> Color(0xFF808080)
            COBBLESTONE -> Color(0xFF696969)
            WOOD_LOG -> {
                if (face == 0 || face == 1) Color(0xFFD2B48C) // Wood rings (light brown)
                else Color(0xFF5C4033) // Dark bark
            }
            OAK_PLANKS -> Color(0xFFCD853F)
            LEAVES -> Color(0xFF2E7D32)
            SAND -> Color(0xFFF4A460)
            WATER -> Color(0x992196F3) // Semi-transparent blue
            COAL_ORE -> {
                if (face == 0) Color(0xFF808080)
                else Color(0xFF555555) // Stone with coal spots
            }
            IRON_ORE -> {
                if (face == 0) Color(0xFF808080)
                else Color(0xFFB08D75) // Stone with iron spots
            }
            DIAMOND_ORE -> {
                if (face == 0) Color(0xFF808080)
                else Color(0xFF50B0D0) // Stone with diamond spots
            }
            CRAFTING_TABLE -> {
                if (face == 0) Color(0xFFCD853F) // Top wood
                else if (face == 1) Color(0xFF5C4033)
                else Color(0xFFA0522D) // Sides with tools detail
            }
        }
    }
}
