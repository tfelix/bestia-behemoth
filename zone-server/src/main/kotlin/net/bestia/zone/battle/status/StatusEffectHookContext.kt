package net.bestia.zone.battle.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a status effect gets to act with when it lands, leaves or is hit. Like [StatusEffectTickContext], the
 * service is handed in rather than injected into the script, because its script registry holds every script.
 */
class StatusEffectHookContext(
  val world: World,
  val hostId: EntityId,
  val effect: ActiveStatusEffect,
  private val statusEffectService: StatusEffectService,
) {

  /** Credited to whoever gave this effect, so a follow-up effect blames the same source. */
  fun applyEffect(targetId: EntityId, effect: StatusEffectId, level: Int, durationSeconds: Double? = null) {
    statusEffectService.applyEffect(
      world, targetId, effect.id, level, sourceEntityId = this.effect.sourceEntityId, durationSeconds = durationSeconds
    )
  }

  fun removeSelf() {
    statusEffectService.removeEffect(world, hostId, effect.definitionId, RemovalReason.BROKEN)
  }
}
