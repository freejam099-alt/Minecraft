package com.example.game

data class CraftingRecipe(
    val id: String,
    val output: GameItem,
    val outputCount: Int,
    val ingredients: Map<GameItem, Int>,
    val requiresTable: Boolean,
    val description: String
)

object CraftingManager {
    val recipes = listOf(
        CraftingRecipe(
            id = "planks",
            output = GameItem.OAK_PLANKS_ITEM,
            outputCount = 4,
            ingredients = mapOf(GameItem.WOOD_LOG_ITEM to 1),
            requiresTable = false,
            description = "Basic wooden planks for building and tools."
        ),
        CraftingRecipe(
            id = "sticks",
            output = GameItem.STICK,
            outputCount = 4,
            ingredients = mapOf(GameItem.OAK_PLANKS_ITEM to 2),
            requiresTable = false,
            description = "Wooden sticks used to craft handles."
        ),
        CraftingRecipe(
            id = "crafting_table",
            output = GameItem.CRAFTING_TABLE_ITEM,
            outputCount = 1,
            ingredients = mapOf(GameItem.OAK_PLANKS_ITEM to 4),
            requiresTable = false,
            description = "Unlocks advanced tools and weapon recipes."
        ),
        CraftingRecipe(
            id = "wooden_sword",
            output = GameItem.WOODEN_SWORD,
            outputCount = 1,
            ingredients = mapOf(GameItem.OAK_PLANKS_ITEM to 2, GameItem.STICK to 1),
            requiresTable = true,
            description = "Deals +4.0 Attack Damage."
        ),
        CraftingRecipe(
            id = "wooden_pickaxe",
            output = GameItem.WOODEN_PICKAXE,
            outputCount = 1,
            ingredients = mapOf(GameItem.OAK_PLANKS_ITEM to 3, GameItem.STICK to 2),
            requiresTable = true,
            description = "Allows mining Stone and Coal. Speed 2.0x."
        ),
        CraftingRecipe(
            id = "stone_sword",
            output = GameItem.STONE_SWORD,
            outputCount = 1,
            ingredients = mapOf(GameItem.COBBLESTONE_ITEM to 2, GameItem.STICK to 1),
            requiresTable = true,
            description = "Deals +5.0 Attack Damage."
        ),
        CraftingRecipe(
            id = "stone_pickaxe",
            output = GameItem.STONE_PICKAXE,
            outputCount = 1,
            ingredients = mapOf(GameItem.COBBLESTONE_ITEM to 3, GameItem.STICK to 2),
            requiresTable = true,
            description = "Allows mining Iron Ore. Speed 4.0x."
        ),
        CraftingRecipe(
            id = "iron_ingot",
            output = GameItem.IRON_INGOT,
            outputCount = 1,
            ingredients = mapOf(GameItem.IRON_ORE_ITEM to 1, GameItem.COAL to 1),
            requiresTable = true,
            description = "Smelt Iron Ore using Coal to get pure Iron Ingots."
        ),
        CraftingRecipe(
            id = "iron_sword",
            output = GameItem.IRON_SWORD,
            outputCount = 1,
            ingredients = mapOf(GameItem.IRON_INGOT to 2, GameItem.STICK to 1),
            requiresTable = true,
            description = "Deals +6.0 Attack Damage."
        ),
        CraftingRecipe(
            id = "iron_pickaxe",
            output = GameItem.IRON_PICKAXE,
            outputCount = 1,
            ingredients = mapOf(GameItem.IRON_INGOT to 3, GameItem.STICK to 2),
            requiresTable = true,
            description = "Allows mining Diamond Ore. Speed 6.0x."
        ),
        CraftingRecipe(
            id = "diamond_sword",
            output = GameItem.DIAMOND_SWORD,
            outputCount = 1,
            ingredients = mapOf(GameItem.DIAMOND to 2, GameItem.STICK to 1),
            requiresTable = true,
            description = "Ultimate legendary blade. Deals +7.0 Attack Damage."
        ),
        CraftingRecipe(
            id = "diamond_pickaxe",
            output = GameItem.DIAMOND_PICKAXE,
            outputCount = 1,
            ingredients = mapOf(GameItem.DIAMOND to 3, GameItem.STICK to 2),
            requiresTable = true,
            description = "Legendary efficiency mining. Speed 8.0x."
        )
    )

    fun canCraft(recipe: CraftingRecipe, player: Player, nearCraftingTable: Boolean): Boolean {
        if (recipe.requiresTable && !nearCraftingTable) return false
        
        for ((ingredient, count) in recipe.ingredients) {
            val owned = player.inventory.getOrDefault(ingredient, 0)
            if (owned < count) return false
        }
        return true
    }

    fun craft(recipe: CraftingRecipe, player: Player, nearCraftingTable: Boolean): Boolean {
        if (!canCraft(recipe, player, nearCraftingTable)) return false

        // Remove ingredients
        for ((ingredient, count) in recipe.ingredients) {
            player.removeItem(ingredient, count)
        }

        // Add output item
        player.addItem(recipe.output, recipe.outputCount)
        return true
    }
}
