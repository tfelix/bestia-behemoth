package net.bestia.zone.ecs.respawn

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.geometry.Vec3L

/**
 * Transient intent: put this entity back on its feet at [position] on the next tick.
 *
 * Carries the destination rather than looking it up, because a save point lives in the database and
 * everything that raises the intent - the respawn message handler, the disconnect listener - already
 * runs off the tick thread. [RespawnSystem] therefore does no I/O at all.
 */
data class Respawn(val position: Vec3L) : Component
