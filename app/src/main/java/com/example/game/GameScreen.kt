package com.example.game

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun GameScreen(
    viewModel: GameViewModel = viewModel()
) {
    val mobs by viewModel.mobs.collectAsState()
    val timeOfDay by viewModel.timeOfDay.collectAsState()
    val isInventoryOpen by viewModel.isInventoryOpen.collectAsState()
    val nearCraftingTable by viewModel.nearCraftingTable.collectAsState()

    val player = viewModel.player
    val voxelRenderer = remember { VoxelRenderer() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Core 3D Game Viewport Canvas
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Capture drag events on the right/center parts of the viewport to rotate camera
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { /* No-op */ },
                        onDragEnd = {
                            viewModel.cameraLookX = 0f
                            viewModel.cameraLookY = 0f
                        },
                        onDragCancel = {
                            viewModel.cameraLookX = 0f
                            viewModel.cameraLookY = 0f
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        // Right-thumb screen drag moves camera looking angle
                        viewModel.cameraLookX = dragAmount.x
                        viewModel.cameraLookY = dragAmount.y
                    }
                }
                .testTag("game_viewport_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                voxelRenderer.render(
                    drawScope = this,
                    world = viewModel.world,
                    player = player,
                    mobs = mobs,
                    zombieHitTimestamps = viewModel.zombieHitTimestamps,
                    timeOfDay = timeOfDay
                )
            }
        }

        // 2. HUD - Left Top Player Coordinates & Status Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Stats Board (Coordinate HUD)
            Column(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                Text(
                    text = "X: ${String.format("%.1f", player.x)}  Y: ${String.format("%.1f", player.y)}  Z: ${String.format("%.1f", player.z)}",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Yaw: ${String.format("%.1f", player.yaw)}  Pitch: ${String.format("%.1f", player.pitch)}",
                    color = Color.LightGray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "FPS: 60 (Smooth 3D Voxel Engine)",
                    color = Color(0xFF4CAF50),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                if (nearCraftingTable) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "📦 Crafting Table Nearby",
                            color = Color(0xFFFFB347),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Procedural Clock & Toggle Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Procedural Sun/Moon Clock HUD Icon
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                        .padding(4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val t = timeOfDay % 24000
                    val isDay = t in 0 until 12000 || t in 23000 until 24000
                    Text(
                        text = if (isDay) "☀️" else "🌙",
                        fontSize = 18.sp
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Inventory HUD Button
                IconButton(
                    onClick = { viewModel.toggleInventory() },
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .testTag("inventory_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShoppingBag,
                        contentDescription = "Inventory",
                        tint = Color.White
                    )
                }
            }
        }

        // 3. Health & Hunger indicators (Lower center)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-110).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Health row
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fullHearts = floor(player.health / 2f).toInt()
                val hasHalf = (player.health % 2f) >= 1.0f
                for (i in 0 until 10) {
                    val heartChar = when {
                        i < fullHearts -> "❤️"
                        i == fullHearts && hasHalf -> "💔"
                        else -> "🖤"
                    }
                    Text(text = heartChar, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 1.dp))
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Hunger row
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fullShanks = floor(player.hunger / 2f).toInt()
                for (i in 0 until 10) {
                    val foodChar = if (i < fullShanks) "🍗" else "🦴"
                    Text(text = foodChar, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 1.dp))
                }
            }
        }

        // 4. Touch Controls HUD - Dual Virtual Joystick & Action Buttons
        // Bottom Layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // LEFT SIDE: Movement Virtual Joystick
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(130.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                var joystickOffset by remember { mutableStateOf(Offset.Zero) }
                val maxRadiusPx = with(LocalDensity.current) { 45.dp.toPx() }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(joystickOffset.x.toInt(), joystickOffset.y.toInt()) }
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White, Color.Gray)
                            )
                        )
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { /* No-op */ },
                                onDragEnd = {
                                    joystickOffset = Offset.Zero
                                    viewModel.movementJoystickX = 0f
                                    viewModel.movementJoystickY = 0f
                                },
                                onDragCancel = {
                                    joystickOffset = Offset.Zero
                                    viewModel.movementJoystickX = 0f
                                    viewModel.movementJoystickY = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                val newOffset = joystickOffset + dragAmount
                                val dist = sqrt(newOffset.x * newOffset.x + newOffset.y * newOffset.y)
                                
                                if (dist <= maxRadiusPx) {
                                    joystickOffset = newOffset
                                } else {
                                    val angle = atan2(newOffset.y, newOffset.x)
                                    joystickOffset = Offset(cos(angle) * maxRadiusPx, sin(angle) * maxRadiusPx)
                                }

                                // Map joystick coordinates to movement inputs:
                                // inputX corresponds to Left/Right strafe (-1 to 1)
                                // inputY corresponds to Forward/Backward (-1 to 1)
                                viewModel.movementJoystickX = joystickOffset.x / maxRadiusPx
                                viewModel.movementJoystickY = -joystickOffset.y / maxRadiusPx // invert Y
                            }
                        }
                        .testTag("movement_joystick")
                )
            }

            // RIGHT SIDE: Retro Mine/Build & Jump Buttons
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                // Secondary Action: Eat button (visible only if food item selected)
                val activeItem = player.getSelectedItem()
                if (activeItem?.isFood == true) {
                    Button(
                        onClick = { viewModel.eatFood() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB347),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag("eat_button")
                    ) {
                        Text("🍎 Eat ${activeItem.displayName}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // MINE / PLACE main trigger action
                    Button(
                        onClick = { viewModel.performAction() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeItem?.isBlock == true) Color(0xFF4CAF50) else Color(0xFFE53935),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(72.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                            .testTag("action_swing_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = if (activeItem?.isBlock == true) "🔨" else "⚔️",
                                fontSize = 20.sp
                            )
                            Text(
                                text = if (activeItem?.isBlock == true) "PLACE" else "MINE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // JUMP button
                    Button(
                        onClick = { viewModel.jumpRequested = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray.copy(alpha = 0.8f),
                            contentColor = Color.White
                        ),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(64.dp)
                            .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                            .testTag("jump_button"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Jump",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        // 5. Floating Bottom Hotbar (Quick Slot Selection)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-16).dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 5) {
                val item = player.hotbar[i]
                val isSelected = player.selectedHotbarSlot == i
                val borderColor = if (isSelected) Color(0xFFFFD700) else Color.Transparent

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.4f)
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) Color(0xFFFFD700) else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .clickable { viewModel.selectHotbarSlot(i) }
                        .testTag("hotbar_slot_$i"),
                    contentAlignment = Alignment.Center
                ) {
                    if (item != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = item.iconEmoji, fontSize = 20.sp)
                            val count = player.inventory.getOrDefault(item, 0)
                            if (count > 1) {
                                Text(
                                    text = "$count",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .align(Alignment.End)
                                        .padding(end = 2.dp)
                                )
                            }
                        }
                    } else {
                        Text(text = "·", color = Color.White.copy(alpha = 0.3f), fontSize = 14.sp)
                    }
                }
            }
        }

        // 6. Responsive Inventory & Crafting Overlay Menu
        AnimatedVisibility(
            visible = isInventoryOpen,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            InventoryCraftingOverlay(
                viewModel = viewModel,
                onClose = { viewModel.toggleInventory() },
                nearTable = nearCraftingTable
            )
        }
    }
}

@Composable
fun InventoryCraftingOverlay(
    viewModel: GameViewModel,
    onClose: () -> Unit,
    nearTable: Boolean
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Inventory, 1: Crafting
    val player = viewModel.player

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        color = Color(0xFF1E1E2C),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Tab Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    Text(
                        text = "🎒 Inventory",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 0) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeTab = 0 }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (activeTab == 0) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "🔨 Crafting",
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activeTab == 1) Color.White.copy(alpha = 0.15f) else Color.Transparent)
                            .clickable { activeTab = 1 }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (activeTab == 1) Color.White else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("inventory_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // TAB Content
            if (activeTab == 0) {
                // TAB 0: Player Inventory Grid & Equipping
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tap any item below to equip it into your active bottom hotbar slot:",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    val inventoryItems = player.inventory.keys.toList()

                    if (inventoryItems.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Your Inventory is empty.\nGo break trees and mine ores to gather items!",
                                color = Color.Gray,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(inventoryItems) { item ->
                                val count = player.inventory.getOrDefault(item, 0)
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.equipItemToHotbar(item, player.selectedHotbarSlot)
                                        },
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color.White.copy(alpha = 0.05f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(text = item.iconEmoji, fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(text = item.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                Text(text = "Quantity: $count", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                        Row {
                                            // Show if equipped in any slot
                                            val hotbarIndex = player.hotbar.indexOf(item)
                                            if (hotbarIndex != -1) {
                                                Text(
                                                    text = "Slot ${hotbarIndex + 1} Equipped",
                                                    color = Color(0xFFFFD700),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier
                                                        .background(Color(0xFF8B7500).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            } else {
                                                Text(
                                                    text = "Tap to Equip",
                                                    color = Color(0xFF4CAF50),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // TAB 1: Advanced Crafting Recipe list
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Smelt materials and assemble legendary tools:",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                        if (!nearTable) {
                            Text(
                                text = "⚠️ Wood logs/sticks can be crafted. Crafting Table required for tools!",
                                color = Color(0xFFE53935),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(CraftingManager.recipes) { recipe ->
                            val isCraftable = CraftingManager.canCraft(recipe, player, nearTable)
                            val hasIngredientsText = recipe.ingredients.entries.joinToString(", ") { (item, count) ->
                                "${item.iconEmoji} ${player.inventory.getOrDefault(item, 0)}/$count"
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = isCraftable) {
                                        viewModel.craftItem(recipe)
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCraftable) Color(0xFF2E3B2E) else Color.White.copy(alpha = 0.03f)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = if (isCraftable) Color(0xFF4CAF50).copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(text = recipe.output.iconEmoji, fontSize = 28.sp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "${recipe.output.displayName} x${recipe.outputCount}",
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (recipe.requiresTable) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Icon(
                                                        imageVector = Icons.Filled.Lock,
                                                        contentDescription = "Requires Table",
                                                        tint = Color(0xFFFFB347),
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                            Text(text = recipe.description, color = Color.LightGray, fontSize = 10.sp)
                                            Text(
                                                text = "Required: $hasIngredientsText",
                                                color = if (isCraftable) Color(0xFF81C784) else Color.Gray,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { viewModel.craftItem(recipe) },
                                        enabled = isCraftable,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF4CAF50),
                                            disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (isCraftable) "CRAFT" else "LOCKED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isCraftable) Color.White else Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
