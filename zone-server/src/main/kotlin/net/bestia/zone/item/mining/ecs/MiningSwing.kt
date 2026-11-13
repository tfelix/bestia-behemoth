package net.bestia.zone.item.mining.ecs

import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.battle.ecs.skill.CastingComponentSMSG
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Countdown
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/** A master swinging a pickaxe at the rock in [ball]. It shows on the cast bar and breaks off like digging. */
class MiningSwing(
  val accountId: Long,
  val ball: CarveBrush,
  val totalSeconds: Float,
) : Countdown(totalSeconds), Removable {

  init {
    require(totalSeconds > 0f) { "MiningSwing requires a positive totalSeconds, got $totalSeconds" }
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
