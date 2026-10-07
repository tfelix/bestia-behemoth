package net.bestia.zone.battle.damage

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.PlayerOwnership
import net.bestia.zone.util.EntityId

/**
 * The one answer to "may this harm land". Every path that hurts an entity asks here, so a new kind of
 * protection is one change in this object rather than one per path.
 */
object DamageGate {

  /** Folded into the reads of every system that asks, so the scheduler sees what the gate looks at. */
  val READS: ComponentClassSet = setOf(StatusEffects::class) + PlayerOwnership.READS

  /**
   * Asked where damage is staged, not only where it is drained: by the drain the client has already been
   * shown a number it would never see subtracted.
   */
  fun verdict(world: World, sourceId: EntityId?, targetId: EntityId): Verdict {
    if (isImmune(world, targetId)) {
      return Verdict.IMMUNE
    }

    if (isOwn(world, sourceId, targetId)) {
      return Verdict.OWN
    }

    if (isWarded(world, sourceId, targetId)) {
      return Verdict.WARDED
    }

    return Verdict.ADMITTED
  }

  /** For the paths that have no source to weigh: the drain, the weather, a trap. */
  fun isImmune(world: World, targetId: EntityId): Boolean {
    return hasShield(world, targetId, HarmShield.ALL)
  }

  /** Nothing harms itself, nor anything else its owner account owns: a master, its bestias, its stations. */
  private fun isOwn(world: World, sourceId: EntityId?, targetId: EntityId): Boolean {
    if (sourceId == null) {
      return false
    }

    if (sourceId == targetId) {
      return true
    }

    val owner = PlayerOwnership.ownerAccountOf(world, sourceId) ?: return false

    return owner == PlayerOwnership.ownerAccountOf(world, targetId)
  }

  /**
   * Harm between two player-owned sides while either one carries [HarmShield.PLAYERS]. A source that is gone,
   * like a fire whose caster logged out, is taken for a player's: that errs towards protection.
   */
  private fun isWarded(world: World, sourceId: EntityId?, targetId: EntityId): Boolean {
    if (sourceId == null || !PlayerOwnership.isPlayerOwned(world, targetId)) {
      return false
    }

    if (!world.isAlive(sourceId)) {
      return hasShield(world, targetId, HarmShield.PLAYERS)
    }

    return PlayerOwnership.isPlayerOwned(world, sourceId) &&
        (hasShield(world, targetId, HarmShield.PLAYERS) || hasShield(world, sourceId, HarmShield.PLAYERS))
  }

  private fun hasShield(world: World, id: EntityId, shield: HarmShield): Boolean {
    return world.get(id, StatusEffects::class)?.hasShield(shield) == true
  }

  enum class Verdict {
    ADMITTED,
    IMMUNE,
    OWN,
    WARDED,
  }
}
