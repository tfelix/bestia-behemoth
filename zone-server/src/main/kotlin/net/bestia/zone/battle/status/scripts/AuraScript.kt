package net.bestia.zone.battle.status.scripts

import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.battle.Sides
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId

/**
 * A field around its bearer: every pulse, whoever the field [reaches] and stands within [radiusTiles] gets
 * [granted] for [lingerSeconds]. Leaving the field ends nothing at once - the granted status simply runs out a few
 * seconds later. A bard's song is one, so is a priest's mana drain.
 *
 * The bearer's own status is what the player sees and cancels; the granted one is what does the work.
 */
abstract class AuraScript(private val aoi: EntityAOIService) : StatusEffectScript {

  override val tickIntervalSeconds: Float = PULSE_SECONDS

  /** Re-cast after a relog: a field is a performance in progress, not a state to restore. */
  override val isPersisted: Boolean = false

  protected abstract val granted: StatusEffectId

  /** How long the granted status outlives the last pulse that reached its bearer. */
  protected open val lingerSeconds: Double = DEFAULT_LINGER_SECONDS

  protected abstract fun radiusTiles(effect: ActiveStatusEffect): Long

  /** Whether [otherId] is the kind of entity this field acts on, seen from [bearerId]. */
  protected abstract fun reaches(world: World, bearerId: EntityId, otherId: EntityId): Boolean

  override fun onTick(context: StatusEffectTickContext) {
    val centre = context.world.get(context.hostId, Position::class)?.toVec3L() ?: return
    val radius = radiusTiles(context.effect)

    for (id in aoi.queryEntitiesInCube(centre, radius * 2)) {
      val at = context.world.get(id, Position::class)?.toVec3L() ?: continue
      if (!isWithin(centre, at, radius) || !reaches(context.world, context.hostId, id)) continue

      context.applyEffect(id, granted, context.effect.level, lingerSeconds, context.effect.amount)
    }
  }

  /** Height is ignored: a song carries up the stairs. */
  private fun isWithin(centre: Vec3L, at: Vec3L, radius: Long): Boolean {
    val dx = at.x - centre.x
    val dy = at.y - centre.y

    return dx * dx + dy * dy <= radius * radius
  }

  companion object {
    const val PULSE_SECONDS = 1f
    const val DEFAULT_LINGER_SECONDS = 5.0

    /** The bearer and everyone not hostile to it: a song is for the bard's side of the fight. */
    fun isFriendOf(world: World, bearerId: EntityId, otherId: EntityId): Boolean {
      return otherId == bearerId || Sides.areAllied(world, bearerId, otherId)
    }

    /** The other side of the fight only. */
    fun isFoeOf(world: World, bearerId: EntityId, otherId: EntityId): Boolean {
      return otherId != bearerId && !Sides.areAllied(world, bearerId, otherId)
    }
  }
}
