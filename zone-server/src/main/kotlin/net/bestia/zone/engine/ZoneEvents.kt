package net.bestia.zone.engine

import net.bestia.zone.ecs2.EntityId
import net.bestia.zone.geometry.Vec3L

/**
 * Domain events emitted by ECS systems onto the [net.bestia.zone.ecs2.World] outbox and consumed by
 * the [ZoneEngine] after each tick. This replaces the old `ZoneServer.queueExternalJob` calls that
 * systems used to fan work out to the network / other services: systems now only emit data and the
 * engine turns it into the (thread-offloaded) side effects.
 */
sealed interface ZoneEvent

/**
 * An entity died. The engine broadcasts the death (vanish) animation to nearby players and, if
 * [lootBestiaId] is set, spawns the bestia's loot at [position].
 */
data class EntityDiedEvent(
  val entityId: EntityId,
  val position: Vec3L,
  val lootBestiaId: Long?,
) : ZoneEvent
