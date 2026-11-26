package net.bestia.zone.worldboss

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L

/**
 * Lets everyone in the world feel that something huge stirred at [feel]'s spot: a feeling with a direction, never a
 * place name. Only very large things use it, such as a world boss waking. Implemented by the chat, which sits above.
 */
fun interface WorldTremors {

  fun feel(world: World, at: Vec3L)
}
