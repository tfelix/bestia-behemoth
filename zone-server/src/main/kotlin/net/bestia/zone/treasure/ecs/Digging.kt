package net.bestia.zone.treasure.ecs

import net.bestia.worldgen.treasure.TreasureCache
import net.bestia.zone.battle.ecs.skill.CastingComponentSMSG
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Countdown
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.treasure.TreasureTrench
import net.bestia.zone.util.EntityId

/**
 * An entity digging one metre of a trench. It shows on the cast bar, like a craft, and whatever breaks a craft
 * breaks it: see `CastCancelService.cancelCraft`.
 *
 * @param cache the treasure cache below, or null when nothing is buried here.
 */
class Digging(
  val accountId: Long,
  val cache: TreasureCache?,
  val trench: TreasureTrench,
  val toDepth: Double,
  val totalSeconds: Float,
) : Countdown(totalSeconds), Removable {

  init {
    require(totalSeconds > 0f) { "Digging requires a positive totalSeconds, got $totalSeconds" }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG {
    return CastingComponentSMSG(
      entityId = entityId,
      remainingSeconds = remainingSeconds.coerceAtLeast(0f),
      totalSeconds = totalSeconds,
      removed = removed
    )
  }

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets {
    return SyncTargets.PublicInRange
  }
}
