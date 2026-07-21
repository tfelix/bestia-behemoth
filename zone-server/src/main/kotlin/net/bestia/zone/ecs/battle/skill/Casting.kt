package net.bestia.zone.ecs.battle.skill

import net.bestia.zone.ecs.Removable
import net.bestia.zone.ecs.SyncTargets
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

/**
 * Marks an entity as channelling a skill with a cast time. It carries everything needed to resolve
 * the skill once [remainingSeconds] hits zero, so the original activation message does not have to be
 * kept around. [CastingSystem] drives the countdown and hands the finished cast to
 * [net.bestia.zone.battle.skill.SkillExecutionService].
 *
 * Synced to everyone in range via the [Dirtyable] pipeline so bystanders see the cast bar too. It is
 * [Removable]: cancelling a cast is simply removing this component, which re-sends
 * [CastingComponentSMSG] with `removed = true` - the client reads that as "cast over", deliberately
 * the same signal for a completed and an interrupted cast, since either way the bar just disappears.
 */
class Casting(
  val skillId: Long,
  val skillLevel: Int,
  /**
   * Set for ENEMY/FRIENDLY targeted skills, null for (AOE_)GROUND ones - exactly one of this and
   * [targetPosition] is non-null.
   */
  val targetEntityId: EntityId?,
  val targetPosition: Vec3L?,
  val totalSeconds: Float,
  remainingSeconds: Float = totalSeconds,
) : Component, Removable {

  var remainingSeconds: Float = remainingSeconds
    set(value) {
      if (field != value) {
        field = value
        dirty = true
      }
    }

  private var dirty = true

  init {
    require(totalSeconds > 0f) { "Casting requires a positive totalSeconds, got $totalSeconds" }
    require(targetEntityId != null || targetPosition != null) {
      "Casting needs either a target entity or a target position"
    }
  }

  override fun isDirty(): Boolean = dirty

  override fun markDirty() {
    dirty = true
  }

  override fun clearDirty() {
    dirty = false
  }

  override fun toEntityMessage(entityId: Long, removed: Boolean): EntitySMSG =
    CastingComponentSMSG(
      entityId = entityId,
      remainingSeconds = remainingSeconds.coerceAtLeast(0f),
      totalSeconds = totalSeconds,
      removed = removed
    )

  // Bystanders see the cast bar as well, so this is a public broadcast rather than owner-only.
  override fun syncTargets(world: World, entityId: EntityId): SyncTargets = SyncTargets.PublicInRange
}
