package net.bestia.zone.prop

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.GrowthStage
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.entity.ecs.PropPose
import net.bestia.zone.entity.ecs.PropVitality
import net.bestia.zone.entity.ecs.StaticSync
import net.bestia.zone.entity.ecs.StaticVisual
import net.bestia.zone.entity.ecs.WorldObjectIdentity
import net.bestia.zone.prop.persistence.DivergenceState
import org.springframework.stereotype.Service
import java.time.Clock

/**
 * What a generated prop leaves behind when it is removed, whatever removed it.
 *
 * Every path that removes a prop comes through here: felling and burning (by way of its death), collecting,
 * the ground being carved away under it, a town being razed. So a removed tree leaves a stump however it went,
 * and a new way to remove a prop cannot forget it.
 *
 * Tick thread only, like the residency and the divergence registry it writes to.
 */
@Service
class PropRemoval(
  private val kinds: PropKindRegistry,
  private val divergence: WorldObjectDivergenceRegistry,
  private val residency: WorldObjectResidencyService,
  private val clock: Clock = Clock.systemUTC()
) {

  enum class Cause {
    /** Felled, burnt, blown up or collected. The kind decides what is left and whether it comes back. */
    USED_UP,

    /** Its ground was carved away, or its town razed. Nothing is left and nothing comes back. */
    GONE_FOR_GOOD
  }

  /**
   * Removes the resident prop [entityId] and leaves what its kind leaves: a stump, or nothing.
   *
   * A prop without a generated name - a player's structure - is not handled here and is left alone.
   */
  fun removed(world: World, entityId: Long, cause: Cause) {
    val identity = world.get(entityId, WorldObjectIdentity::class) ?: return
    val visual = world.get(entityId, StaticVisual::class) ?: return
    if (identity.propId == 0L) return

    when (record(identity.propId, visual.kind, cause)) {
      GrowthStage.STUMP -> residency.replace(world, entityId, GrowthStage.STUMP)
      else -> residency.remove(world, entityId)
    }
  }

  /** As [removed], for a prop whose column nobody holds. What it leaves appears when the column next loads. */
  fun removedUnseen(propId: Long, kind: StaticEntityKind, cause: Cause) {
    record(propId, kind, cause)
  }

  /** @return what now stands at the prop's place, or null for nothing */
  private fun record(propId: Long, kind: StaticEntityKind, cause: Cause): GrowthStage? {
    val spec = kinds.of(kind)
    val now = clock.instant()

    if (cause == Cause.GONE_FOR_GOOD) {
      divergence.recordDepletion(propId, kind, resumeAt = null)
      return null
    }

    val regrowth = spec.regrowth
    if (regrowth != null) {
      divergence.record(propId, kind, DivergenceState.STUMP, now.plusSeconds(regrowth.stageSeconds))
      return GrowthStage.STUMP
    }

    divergence.recordDepletion(propId, kind, spec.regrowSeconds?.let { now.plusSeconds(it) })
    return null
  }

  companion object {
    /**
     * What a system that calls [removed] has to declare among its writes: leaving a stump creates a prop
     * entity, and the world refuses components a running system did not declare.
     */
    val WRITES: ComponentClassSet = setOf(
      PropPose::class, StaticVisual::class, PropVitality::class, WorldObjectIdentity::class, StaticSync::class
    )
  }
}
