package net.bestia.zone.ecs.spawn.ambient

import net.bestia.zone.ecs.core.Component

/**
 * Marks a creature the ambient layer put in the world, and names the lattice cell it belongs to.
 *
 * Not `Dirtyable`, for `DenMember`'s reason: which cell a creature came from is server bookkeeping and the
 * client has no use for it.
 *
 * Absence is meaningful, exactly as with `DenMember`: a creature without this came from a den, a script, a
 * GM command or a player, and none of those may be torn down by [AmbientSpawnerSystem] or throttled by
 * `AiThrottle`. That is what makes the level-of-detail opt-in rather than a rule about distance.
 */
data class Ambient(val cell: Long) : Component
