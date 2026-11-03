package net.bestia.zone.battle.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a ticking status effect gets to act with. The effect service is handed in rather than injected into
 * the script, because the service's script registry holds every script and would close a dependency cycle.
 */
class StatusEffectTickContext(
  val world: World,
  val hostId: EntityId,
  val effect: ActiveStatusEffect,
  private val statusEffectService: StatusEffectService,
) {

  val level: Int
    get() = effect.level

  /** Credited to the host, so the target's effect names who gave it. */
  fun applyEffect(targetId: EntityId, effect: StatusEffectId, level: Int) {
    statusEffectService.applyEffect(world, targetId, effect, level, sourceEntityId = hostId)
  }

  /**
   * Hurts the host, credited to whoever gave the effect so a poison's kill goes to the poisoner. Staged like a
   * blow, so `ReceivedDamageSystem` handles death and interrupts a cast.
   */
  fun dealDamage(amount: Int) {
    if (amount <= 0) return

    val staged = world.get(hostId, IncomingDamage::class) ?: world.add(hostId, IncomingDamage())
    staged.add(amount, effect.sourceEntityId ?: hostId)
  }

  fun removeSelf() {
    statusEffectService.removeEffect(world, hostId, effect.definitionId, RemovalReason.CANCELLED)
  }
}
