package com.example.game

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

enum class MobType {
    ZOMBIE
}

class Mob(
    val id: String,
    var x: Float,
    var y: Float,
    var z: Float,
    val type: MobType = MobType.ZOMBIE
) {
    var vx = 0.0f
    var vy = 0.0f
    var vz = 0.0f

    var health = 15.0f
    val maxHealth = 15.0f

    var onGround = false
    var attackCooldown = 0L

    fun update(player: Player, world: World, soundEffects: SoundEffects?) {
        if (health <= 0) return

        // 1. Pathfind towards player if in detection range (14 blocks)
        val dx = player.x - x
        val dy = player.y - y
        val dz = player.z - z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        if (distance < 14.0f) {
            val angle = atan2(dy, dx)
            val speed = 0.035f

            vx = cos(angle) * speed
            vy = sin(angle) * speed

            // Attack player if very close (1.1 blocks)
            if (distance < 1.1f) {
                val now = System.currentTimeMillis()
                if (now - attackCooldown > 1200L) { // 1.2s attack speed
                    player.health = (player.health - 2.5f).coerceAtLeast(0.0f) // 1.25 hearts
                    // Apply knockback to player
                    player.vx = cos(angle) * 0.15f
                    player.vy = sin(angle) * 0.15f
                    player.vz = 0.1f // small pop up
                    attackCooldown = now
                    soundEffects?.playPlayerDamage()
                }
            }
        } else {
            // Idle wander
            vx *= 0.5f
            vy *= 0.5f
        }

        // 2. Apply gravity
        vz -= 0.015f
        vz = vz.coerceIn(-0.4f, 0.4f)

        // 3. Obstacle avoidance (jumping)
        // If there is a solid block right in front of its path, jump!
        val nextX = x + vx
        val nextY = y + vy
        val nextZ = z + vz

        // Check if there's a block in front of mob (at foot level or torso level)
        val checkX = x + Math.signum(vx) * 0.4f
        val checkY = y + Math.signum(vy) * 0.4f
        val ix = floor(checkX).toInt()
        val iy = floor(checkY).toInt()
        val izFoot = floor(z).toInt()
        val izChest = floor(z + 1.0f).toInt()

        val blockInFrontFoot = world.getBlock(ix, iy, izFoot)
        val blockInFrontChest = world.getBlock(ix, iy, izChest)

        if (onGround && blockInFrontFoot.isSolid && !blockInFrontChest.isSolid) {
            vz = 0.20f // Jump!
            onGround = false
            soundEffects?.playJump()
        }

        // 4. Position and physics update
        if (canMoveTo(nextX, y, z, world)) {
            x = nextX
        } else {
            vx = 0.0f
        }

        if (canMoveTo(x, nextY, z, world)) {
            y = nextY
        } else {
            vy = 0.0f
        }

        if (vz < 0) {
            if (canMoveTo(x, y, nextZ, world)) {
                z = nextZ
                onGround = false
            } else {
                z = floor(z)
                vz = 0.0f
                onGround = true
            }
        } else {
            if (canMoveTo(x, y, nextZ, world)) {
                z = nextZ
                onGround = false
            } else {
                vz = 0.0f
            }
        }

        // Respawn if falls out of world
        if (z < -5.0f) {
            respawnFarFrom(player, world)
        }
    }

    fun takeDamage(amount: Float, playerAngle: Float, soundEffects: SoundEffects?) {
        health = (health - amount).coerceAtLeast(0.0f)
        soundEffects?.playZombieDamage()
        // Simple knockback
        vx = cos(playerAngle) * 0.18f
        vy = sin(playerAngle) * 0.18f
        vz = 0.1f
    }

    fun respawnFarFrom(player: Player, world: World) {
        val random = Random(System.currentTimeMillis())
        // Spawns 12-20 blocks away
        val angle = random.nextFloat() * 2 * Math.PI.toFloat()
        val dist = 12.0f + random.nextFloat() * 8.0f
        val rx = player.x + cos(angle) * dist
        val ry = player.y + sin(angle) * dist
        
        x = rx.coerceIn(1.0f, (World.WORLD_X - 2).toFloat())
        y = ry.coerceIn(1.0f, (World.WORLD_Y - 2).toFloat())
        
        val surf = world.getSurfaceHeight(floor(x).toInt(), floor(y).toInt())
        z = (surf + 1).toFloat()
        
        health = maxHealth
        vx = 0.0f
        vy = 0.0f
        vz = 0.0f
    }

    private fun canMoveTo(tx: Float, ty: Float, tz: Float, world: World): Boolean {
        val r = 0.3f
        val h = 1.7f

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
                        if (world.getBlock(bx, by, bz).isSolid) {
                            return false
                        }
                    }
                }
            }
        }
        return true
    }
}
