package net.bestia.zone.battle.ecs.skill

import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.sync.Countdown
import net.bestia.zone.sync.Removable
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.util.EntityId

/**
 * A skill still at work after its cast resolved, like Inferno raining fire while its caster holds the spell. It
 * acts every [intervalSeconds] until it runs out. Anything that breaks a cast breaks it, see [CastCancelService].
 * It shows on the same bar as a cast.
 */
class Channel(
  val skillId: Long,
  val skillLevel: Int,
  val targetEntityId: EntityId?,
  val targetPosition: Vec3L?,
  val intervalSeconds: Float,
  val totalSeconds: Float,
) : Countdown(totalSeconds), Removable {

  var sinceLastTick: Float = 0f
  var ticks: Int = 0

  init {
    require(intervalSeconds > 0f) { "A channel needs a positive interval, got $intervalSeconds" }
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG =
    CastingComponentSMSG(
      entityId = entityId,
      remainingSeconds = remainingSeconds.coerceAtLeast(0f),
      totalSeconds = totalSeconds,
      removed = removed
    )

  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
