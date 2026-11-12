package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.ecs.Dead
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 227 (`DEVOTED`): under a Knight's Devotion for `15 s/lv`. Each blow or spell lands on the
 * Knight instead, as much as it would have hurt the bearer. It ends once the two are more than 7 m apart or the
 * Knight falls. A poison or a burn stays with the bearer.
 */
@Component
class Devoted : StatusEffectScript {

  /** It names an entity, and entity ids do not outlive a relog. */
  override val isPersisted: Boolean = false
  override val tickIntervalSeconds: Float = 1f

  override fun durationSeconds(level: Int): Double {
    return SECONDS_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)
  }

  override fun onTick(context: StatusEffectTickContext) {
    if (!isKnightInReach(context.world, context.hostId, context.effect.sourceEntityId)) {
      context.removeSelf()
    }
  }

  override fun onDamageTaken(context: StatusEffectHookContext, hit: IncomingDamage.DamageAmount): Int {
    if (hit.amount <= 0 || hit.kind.form == HitForm.OTHER) return hit.amount
    val knight = context.effect.sourceEntityId
    if (knight == null || !isKnightInReach(context.world, context.hostId, knight)) {
      context.removeSelf()
      return hit.amount
    }

    context.passOn(knight, hit.amount, from = hit.sourceEntityId)

    return 0
  }

  private fun isKnightInReach(world: World, allyId: EntityId, knightId: EntityId?): Boolean {
    if (knightId == null || !world.isAlive(knightId) || world.has(knightId, Dead::class)) return false
    val ally = world.get(allyId, Position::class)?.toVec3L() ?: return false
    val knight = world.get(knightId, Position::class)?.toVec3L() ?: return false

    return ally.distance(knight) <= REACH_METRES
  }

  companion object {
    private const val MAX_LEVEL = 3
    private const val SECONDS_PER_LEVEL = 15.0
    const val REACH_METRES = 7L
  }
}
