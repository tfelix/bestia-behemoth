package net.bestia.zone.item.eating

import net.bestia.zone.battle.ecs.skill.CastingComponentSMSG
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Countdown
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * An entity eating a meal: [playerItemId] stays in its bag until the channel is through, then [EatingSystem] eats
 * it. It shows on the cast bar, and whatever breaks a craft breaks it, see [EatingInterrupt]; nothing is eaten then.
 */
class Eating(
  val playerItemId: Long,
  val itemId: Long,
  val totalSeconds: Float,
) : Countdown(totalSeconds), Removable {

  init {
    require(totalSeconds > 0f) { "Eating requires a positive totalSeconds, got $totalSeconds" }
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
