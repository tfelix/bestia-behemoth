package net.bestia.zone.battle.status

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.ecs.damage.IncomingDamage
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
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

  /** Puts out another effect on the host, as fire puts out ice. */
  fun removeEffect(effect: StatusEffectId) {
    statusEffectService.removeEffect(world, hostId, effect.id, RemovalReason.CANCELLED)
  }

  /** Ends [effect] on another entity, for a spell with a half on each, like a Scry's body and its far eye. */
  fun removeEffectFrom(targetId: EntityId, effect: StatusEffectId) {
    statusEffectService.removeEffect(world, targetId, effect.id, RemovalReason.CANCELLED)
  }

  /** Hurts the host the way [StatusEffectTickContext.dealDamage] does. */
  fun dealDamage(amount: Int) {
    if (amount <= 0) return

    val staged = world.get(hostId, IncomingDamage::class) ?: world.add(hostId, IncomingDamage())
    staged.add(amount, effect.sourceEntityId ?: hostId)
  }

  /**
   * Hurts [targetId] by a share of a hit the host took, like a blow a shield returns, credited to [from]. An untyped
   * hit, so no modifier weighs it twice and no ward passes it on again.
   */
  fun passOn(targetId: EntityId, amount: Int, from: EntityId) {
    if (amount <= 0) return

    // Deferred: a hit is drained inside ReceivedDamageSystem, where adding the component would be queued and a
    // second share in the same tick would replace the first.
    world.defer {
      if (!world.isAlive(targetId)) return@defer

      val staged = world.get(targetId, IncomingDamage::class) ?: world.add(targetId, IncomingDamage())
      staged.add(amount, from)
    }
  }

  /** Starts the effect's clock again at [durationSeconds], as a refresh by a new cast would. */
  fun refreshSelf(durationSeconds: Double) {
    world.get(hostId, StatusEffects::class)?.refresh(effect, durationSeconds)
  }

  fun removeSelf() {
    statusEffectService.removeEffect(world, hostId, effect.definitionId, RemovalReason.CANCELLED)
  }
}
