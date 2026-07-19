package com.example.game

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Point2D(val x: Float, val y: Float, val depth: Float)

class VoxelRenderer {

    // Projects 3D world coordinate to 2D screen coordinate
    fun project(
        x: Float, y: Float, z: Float,
        px: Float, py: Float, pz: Float,
        yaw: Float, pitch: Float,
        width: Float, height: Float
    ): Point2D? {
        // 1. Translate relative to player
        val dx = x - px
        val dy = y - py
        val dz = z - pz

        // 2. Rotate around Z axis (yaw)
        val cosY = cos(yaw)
        val sinY = sin(yaw)
        val rx = dx * cosY - dy * sinY
        val ry = dx * sinY + dy * cosY

        // 3. Rotate around X axis (pitch)
        val cosP = cos(pitch)
        val sinP = sin(pitch)
        val rz = dz * cosP - ry * sinP
        val rdepth = dz * sinP + ry * cosP

        // Near-plane clipping to avoid division by zero or rendering behind player
        if (rdepth < 0.05f) return null

        // 4. Perspective projection
        val focal = width * 0.75f // FOV adjustment
        val sx = (width / 2f) + (rx / rdepth) * focal
        val sy = (height / 2f) - (rz / rdepth) * focal

        return Point2D(sx, sy, rdepth)
    }

    // Helper to calculate distance squared between two points
    private fun distSq(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float): Float {
        val dx = x1 - x2
        val dy = y1 - y2
        val dz = z1 - z2
        return dx * dx + dy * dy + dz * dz
    }

    // Main render loop for the 3D Canvas
    fun render(
        drawScope: DrawScope,
        world: World,
        player: Player,
        mobs: List<Mob>,
        zombieHitTimestamps: Map<String, Long>,
        timeOfDay: Long
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height

        if (width == 0f || height == 0f) return

        val px = player.x
        val py = player.y
        val pz = player.z + 1.62f // Add camera height (eye-level of player)
        val pyaw = player.yaw
        val ppitch = player.pitch

        // 1. Draw the beautiful skybox
        drawSkybox(drawScope, timeOfDay, width, height, pyaw, ppitch)

        // 2. Collect and sort all visible blocks & entities by distance (Painter's Algorithm)
        val viewRadius = 14.0f
        val renderList = mutableListOf<Renderable>()

        // Get all exposed blocks within rendering distance
        val exposedPos = world.getExposedBlocks()
        for (pos in exposedPos) {
            val bx = pos.x.toFloat()
            val by = pos.y.toFloat()
            val bz = pos.z.toFloat()

            val dSq = distSq(bx + 0.5f, by + 0.5f, bz + 0.5f, px, py, pz)
            if (dSq < viewRadius * viewRadius) {
                renderList.add(RenderableBlock(pos, dSq))
            }
        }

        // Add Mobs
        val now = System.currentTimeMillis()
        for (mob in mobs) {
            if (mob.health <= 0) continue
            val dSq = distSq(mob.x, mob.y, mob.z + 0.9f, px, py, pz)
            if (dSq < viewRadius * viewRadius) {
                val isRedFlash = now - zombieHitTimestamps.getOrDefault(mob.id, 0L) < 300L
                renderList.add(RenderableMob(mob, dSq, isRedFlash))
            }
        }

        // Sort from furthest to closest
        renderList.sortByDescending { it.depthSq }

        // 3. Render items back-to-front
        for (item in renderList) {
            when (item) {
                is RenderableBlock -> {
                    drawBlock(drawScope, item.pos, world, px, py, pz, pyaw, ppitch, width, height)
                }
                is RenderableMob -> {
                    drawMob(drawScope, item.mob, item.isRedFlash, px, py, pz, pyaw, ppitch, width, height)
                }
            }
        }

        // 4. Draw a neat crosshair in the center of the screen
        val crosshairSize = 12f
        val strokeWidth = 2.5f
        val color = Color.White.copy(alpha = 0.8f)
        drawScope.drawLine(
            color = color,
            start = Offset(width / 2f - crosshairSize, height / 2f),
            end = Offset(width / 2f + crosshairSize, height / 2f),
            strokeWidth = strokeWidth
        )
        drawScope.drawLine(
            color = color,
            start = Offset(width / 2f, height / 2f - crosshairSize),
            end = Offset(width / 2f, height / 2f + crosshairSize),
            strokeWidth = strokeWidth
        )
    }

    private fun drawSkybox(
        drawScope: DrawScope,
        timeOfDay: Long,
        width: Float,
        height: Float,
        yaw: Float,
        pitch: Float
    ) {
        // Sky colors depending on time
        // Day: 0-12000, Sunset: 12000-13000, Night: 13000-23000, Sunrise: 23000-24000
        val t = timeOfDay % 24000
        val skyColor = when {
            t in 0 until 11000 -> Color(0xFF87CEEB) // Beautiful Sky Blue
            t in 11000 until 13000 -> { // Sunset transition
                val fraction = (t - 11000).toFloat() / 2000f
                interpolateColor(Color(0xFF87CEEB), Color(0xFFFD5E53), fraction) // Blue to Orange/Red
            }
            t in 13000 until 14000 -> { // Sunset to Night
                val fraction = (t - 13000).toFloat() / 1000f
                interpolateColor(Color(0xFFFD5E53), Color(0xFF0D0D1A), fraction) // Orange to Deep Night
            }
            t in 14000 until 22000 -> Color(0xFF0B0B16) // Cosmic Dark Sky
            t in 22000 until 23000 -> { // Sunrise transition
                val fraction = (t - 22000).toFloat() / 1000f
                interpolateColor(Color(0xFF0B0B16), Color(0xFFFFB347), fraction) // Night to Golden Orange
            }
            else -> { // Golden Orange to Sky Blue
                val fraction = (t - 23000).toFloat() / 1000f
                interpolateColor(Color(0xFFFFB347), Color(0xFF87CEEB), fraction)
            }
        }

        drawScope.drawRect(color = skyColor)

        // Draw Stars at night
        if (t in 13000 until 23000) {
            val random = java.util.Random(100) // stable seed for stars
            val starCount = 30
            for (i in 0 until starCount) {
                // Generate relative 3D star coordinates high in the sky
                val sa = random.nextFloat() * 2 * Math.PI.toFloat()
                val sd = 30.0f
                val sx = cos(sa) * sd
                val sy = sin(sa) * sd
                val sz = 15.0f + random.nextFloat() * 15.0f // High up

                val starProj = project(sx, sy, sz, 0f, 0f, 0f, yaw, pitch, width, height)
                if (starProj != null) {
                    val starSize = 2f + random.nextFloat() * 3f
                    val alpha = 0.5f + random.nextFloat() * 0.5f
                    drawScope.drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = starSize,
                        center = Offset(starProj.x, starProj.y)
                    )
                }
            }
        }

        // Draw Sun and Moon
        // Angle of rotation around the player
        val sunAngle = (t.toFloat() / 24000f) * 2 * Math.PI.toFloat() - Math.PI.toFloat() / 2f
        val sunDist = 35.0f
        
        // Sun position
        val sunX = cos(sunAngle) * sunDist
        val sunY = sunDist // fix on Y
        val sunZ = sin(sunAngle) * sunDist

        // Sun is golden yellow
        val sunProj = project(sunX, sunY, sunZ, 0f, 0f, 0f, yaw, pitch, width, height)
        if (sunProj != null) {
            drawScope.drawCircle(
                color = Color(0xFFFFD700),
                radius = (width * 0.04f) / (sunProj.depth * 0.05f).coerceAtLeast(1.0f),
                center = Offset(sunProj.x, sunProj.y)
            )
        }

        // Moon position (opposite to Sun)
        val moonAngle = sunAngle + Math.PI.toFloat()
        val moonX = cos(moonAngle) * sunDist
        val moonY = sunDist
        val moonZ = sin(moonAngle) * sunDist

        // Moon is silver-white
        val moonProj = project(moonX, moonY, moonZ, 0f, 0f, 0f, yaw, pitch, width, height)
        if (moonProj != null) {
            drawScope.drawCircle(
                color = Color(0xFFF0F0F0),
                radius = (width * 0.03f) / (moonProj.depth * 0.05f).coerceAtLeast(1.0f),
                center = Offset(moonProj.x, moonProj.y)
            )
        }
    }

    private fun interpolateColor(c1: Color, c2: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        val r = c1.red + (c2.red - c1.red) * f
        val g = c1.green + (c2.green - c1.green) * f
        val b = c1.blue + (c2.blue - c1.blue) * f
        val a = c1.alpha + (c2.alpha - c1.alpha) * f
        return Color(r, g, b, a)
    }

    private fun drawBlock(
        drawScope: DrawScope,
        pos: BlockPos,
        world: World,
        px: Float, py: Float, pz: Float,
        yaw: Float, pitch: Float,
        width: Float, height: Float
    ) {
        val type = world.getBlock(pos.x, pos.y, pos.z)
        if (type == BlockType.AIR) return

        val x = pos.x.toFloat()
        val y = pos.y.toFloat()
        val z = pos.z.toFloat()

        // Cache 8 projected vertices of the block cube
        val v = Array<Point2D?>(8) { null }
        v[0] = project(x, y, z, px, py, pz, yaw, pitch, width, height)
        v[1] = project(x + 1, y, z, px, py, pz, yaw, pitch, width, height)
        v[2] = project(x, y + 1, z, px, py, pz, yaw, pitch, width, height)
        v[3] = project(x + 1, y + 1, z, px, py, pz, yaw, pitch, width, height)
        v[4] = project(x, y, z + 1, px, py, pz, yaw, pitch, width, height)
        v[5] = project(x + 1, y, z + 1, px, py, pz, yaw, pitch, width, height)
        v[6] = project(x, y + 1, z + 1, px, py, pz, yaw, pitch, width, height)
        v[7] = project(x + 1, y + 1, z + 1, px, py, pz, yaw, pitch, width, height)

        // Draw visible faces based on backface culling
        // Normals: TOP (0,0,1), BOTTOM (0,0,-1), SOUTH (0,-1,0), NORTH (0,1,0), WEST (-1,0,0), EAST (1,0,0)
        
        // 1. TOP face (Z = +1)
        if (pz > z + 1f) {
            val v4 = v[4]; val v5 = v[5]; val v6 = v[6]; val v7 = v[7]
            if (v4 != null && v5 != null && v6 != null && v7 != null) {
                // Neighbors check - only draw face if the block adjacent is transparent/air/water
                if (shouldDrawFace(world, pos.x, pos.y, pos.z + 1, type)) {
                    val color = type.getFaceColor(0)
                    drawFace(drawScope, v[4]!!, v[5]!!, v[7]!!, v[6]!!, color, 1.0f)
                }
            }
        }

        // 2. BOTTOM face (Z = 0)
        if (pz < z) {
            val v0 = v[0]; val v1 = v[1]; val v2 = v[2]; val v3 = v[3]
            if (v0 != null && v1 != null && v2 != null && v3 != null) {
                if (shouldDrawFace(world, pos.x, pos.y, pos.z - 1, type)) {
                    val color = type.getFaceColor(1)
                    drawFace(drawScope, v[0]!!, v[2]!!, v[3]!!, v[1]!!, color, 0.5f) // shaded dark bottom
                }
            }
        }

        // 3. SOUTH face (Y = 0)
        if (py < y) {
            val v0 = v[0]; val v1 = v[1]; val v4 = v[4]; val v5 = v[5]
            if (v0 != null && v1 != null && v4 != null && v5 != null) {
                if (shouldDrawFace(world, pos.x, pos.y - 1, pos.z, type)) {
                    val color = type.getFaceColor(3)
                    drawFace(drawScope, v[0]!!, v[1]!!, v[5]!!, v[4]!!, color, 0.8f) // 80% light on side
                }
            }
        }

        // 4. NORTH face (Y = +1)
        if (py > y + 1f) {
            val v2 = v[2]; val v3 = v[3]; val v6 = v[6]; val v7 = v[7]
            if (v2 != null && v3 != null && v6 != null && v7 != null) {
                if (shouldDrawFace(world, pos.x, pos.y + 1, pos.z, type)) {
                    val color = type.getFaceColor(2)
                    drawFace(drawScope, v[3]!!, v[2]!!, v[6]!!, v[7]!!, color, 0.8f)
                }
            }
        }

        // 5. WEST face (X = 0)
        if (px < x) {
            val v0 = v[0]; val v2 = v[2]; val v4 = v[4]; val v6 = v[6]
            if (v0 != null && v2 != null && v4 != null && v6 != null) {
                if (shouldDrawFace(world, pos.x - 1, pos.y, pos.z, type)) {
                    val color = type.getFaceColor(5)
                    drawFace(drawScope, v[2]!!, v[0]!!, v[4]!!, v[6]!!, color, 0.65f) // 65% light
                }
            }
        }

        // 6. EAST face (X = +1)
        if (px > x + 1f) {
            val v1 = v[1]; val v3 = v[3]; val v5 = v[5]; val v7 = v[7]
            if (v1 != null && v3 != null && v5 != null && v7 != null) {
                if (shouldDrawFace(world, pos.x + 1, pos.y, pos.z, type)) {
                    val color = type.getFaceColor(4)
                    drawFace(drawScope, v[1]!!, v[3]!!, v[7]!!, v[5]!!, color, 0.65f)
                }
            }
        }
    }

    private fun shouldDrawFace(world: World, nx: Int, ny: Int, nz: Int, currentType: BlockType): Boolean {
        val neighbor = world.getBlock(nx, ny, nz)
        if (neighbor == BlockType.AIR) return true
        if (neighbor.isTransparent && neighbor != currentType) return true
        if (neighbor.isLiquid && !currentType.isLiquid) return true
        return false
    }

    private fun drawFace(
        drawScope: DrawScope,
        p1: Point2D, p2: Point2D, p3: Point2D, p4: Point2D,
        baseColor: Color,
        brightness: Float
    ) {
        val path = Path().apply {
            moveTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            lineTo(p4.x, p4.y)
            close()
        }

        // Apply directional brightness to color
        val shadedColor = Color(
            red = (baseColor.red * brightness).coerceIn(0f, 1f),
            green = (baseColor.green * brightness).coerceIn(0f, 1f),
            blue = (baseColor.blue * brightness).coerceIn(0f, 1f),
            alpha = baseColor.alpha
        )

        // Draw face fill
        drawScope.drawPath(path = path, color = shadedColor)

        // Draw subtle outline to make blocks look detailed and pixel-beveled
        val outlineColor = Color.Black.copy(alpha = 0.12f)
        drawScope.drawPath(
            path = path,
            color = outlineColor,
            style = Stroke(width = 1.2f)
        )

        // Draw nice pixel highlights (beveled lighting edges)
        if (brightness >= 0.8f) {
            val highlightColor = Color.White.copy(alpha = 0.15f)
            drawScope.drawLine(
                color = highlightColor,
                start = Offset(p1.x, p1.y),
                end = Offset(p2.x, p2.y),
                strokeWidth = 1.8f
            )
            drawScope.drawLine(
                color = highlightColor,
                start = Offset(p1.x, p1.y),
                end = Offset(p4.x, p4.y),
                strokeWidth = 1.8f
            )
        }
    }

    private fun drawMob(
        drawScope: DrawScope,
        mob: Mob,
        isRedFlash: Boolean,
        px: Float, py: Float, pz: Float,
        yaw: Float, pitch: Float,
        width: Float, height: Float
    ) {
        val mx = mob.x
        val my = mob.y
        val mz = mob.z

        // Render Zombie as stacked cubes: blue Torso, green Head
        val torsoHeight = 0.9f
        val headHeight = 0.5f
        
        // Face colors
        val torsoColor = if (isRedFlash) Color.Red else Color(0xFF1E88E5) // Zombie blue shirt
        val headColor = if (isRedFlash) Color.Red else Color(0xFF4CAF50) // Zombie green skin
        val pantsColor = if (isRedFlash) Color.Red else Color(0xFF3F51B5) // Zombie purple/blue pants

        // Render Pants (bottom half of torso)
        drawCustomModelCube(
            drawScope, mx, my, mz, 0.44f, 0.44f, 0.45f,
            pantsColor, px, py, pz, yaw, pitch, width, height
        )

        // Render Shirt (top half of torso)
        drawCustomModelCube(
            drawScope, mx, my, mz + 0.45f, 0.46f, 0.46f, 0.45f,
            torsoColor, px, py, pz, yaw, pitch, width, height
        )

        // Render Head
        drawCustomModelCube(
            drawScope, mx, my, mz + 0.9f, 0.38f, 0.38f, headHeight,
            headColor, px, py, pz, yaw, pitch, width, height
        )

        // Render Arms extending forward!
        // Zombie arms point forward along the direction of player relative to zombie
        val dx = px - mx
        val dy = py - my
        val zombieAngle = atan2(dy, dx).toFloat()
        val armLength = 0.5f

        // Draw two arms
        val armOffsetLeftX = -0.2f * sin(zombieAngle)
        val armOffsetLeftY = 0.2f * cos(zombieAngle)
        drawCustomModelCube(
            drawScope, mx + armOffsetLeftX, my + armOffsetLeftY, mz + 0.65f, 0.12f, armLength, 0.12f,
            headColor, px, py, pz, yaw, pitch, width, height
        )

        val armOffsetRightX = 0.2f * sin(zombieAngle)
        val armOffsetRightY = -0.2f * cos(zombieAngle)
        drawCustomModelCube(
            drawScope, mx + armOffsetRightX, my + armOffsetRightY, mz + 0.65f, 0.12f, armLength, 0.12f,
            headColor, px, py, pz, yaw, pitch, width, height
        )
    }

    private fun drawCustomModelCube(
        drawScope: DrawScope,
        cx: Float, cy: Float, cz: Float,
        wx: Float, wy: Float, wz: Float,
        color: Color,
        px: Float, py: Float, pz: Float,
        yaw: Float, pitch: Float,
        width: Float, height: Float
    ) {
        val hx = wx / 2f
        val hy = wy / 2f

        val v = Array<Point2D?>(8) { null }
        v[0] = project(cx - hx, cy - hy, cz, px, py, pz, yaw, pitch, width, height)
        v[1] = project(cx + hx, cy - hy, cz, px, py, pz, yaw, pitch, width, height)
        v[2] = project(cx - hx, cy + hy, cz, px, py, pz, yaw, pitch, width, height)
        v[3] = project(cx + hx, cy + hy, cz, px, py, pz, yaw, pitch, width, height)
        v[4] = project(cx - hx, cy - hy, cz + wz, px, py, pz, yaw, pitch, width, height)
        v[5] = project(cx + hx, cy - hy, cz + wz, px, py, pz, yaw, pitch, width, height)
        v[6] = project(cx - hx, cy + hy, cz + wz, px, py, pz, yaw, pitch, width, height)
        v[7] = project(cx + hx, cy + hy, cz + wz, px, py, pz, yaw, pitch, width, height)

        // Draw top face
        if (pz > cz + wz) {
            val v4 = v[4]; val v5 = v[5]; val v6 = v[6]; val v7 = v[7]
            if (v4 != null && v5 != null && v6 != null && v7 != null) {
                drawFace(drawScope, v[4]!!, v[5]!!, v[7]!!, v[6]!!, color, 1.0f)
            }
        }

        // Draw side faces facing player
        if (py < cy - hy) {
            val v0 = v[0]; val v1 = v[1]; val v4 = v[4]; val v5 = v[5]
            if (v0 != null && v1 != null && v4 != null && v5 != null) {
                drawFace(drawScope, v[0]!!, v[1]!!, v[5]!!, v[4]!!, color, 0.8f)
            }
        }
        if (py > cy + hy) {
            val v2 = v[2]; val v3 = v[3]; val v6 = v[6]; val v7 = v[7]
            if (v2 != null && v3 != null && v6 != null && v7 != null) {
                drawFace(drawScope, v[3]!!, v[2]!!, v[6]!!, v[7]!!, color, 0.8f)
            }
        }
        if (px < cx - hx) {
            val v0 = v[0]; val v2 = v[2]; val v4 = v[4]; val v6 = v[6]
            if (v0 != null && v2 != null && v4 != null && v6 != null) {
                drawFace(drawScope, v[2]!!, v[0]!!, v[4]!!, v[6]!!, color, 0.65f)
            }
        }
        if (px > cx + hx) {
            val v1 = v[1]; val v3 = v[3]; val v5 = v[5]; val v7 = v[7]
            if (v1 != null && v3 != null && v5 != null && v7 != null) {
                drawFace(drawScope, v[1]!!, v[3]!!, v[7]!!, v[5]!!, color, 0.65f)
            }
        }
    }
}

// Sealed interfaces for general Renderer Painter's Algorithm sorting
sealed interface Renderable {
    val depthSq: Float
}

data class RenderableBlock(val pos: BlockPos, override val depthSq: Float) : Renderable
data class RenderableMob(val mob: Mob, override val depthSq: Float, val isRedFlash: Boolean) : Renderable
