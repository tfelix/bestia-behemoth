package net.bestia.zone.ecs.battle.buff

import net.bestia.zone.battle.buff.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.buff.StatusEffect
import net.bestia.zone.battle.buff.StatusEffectTriggerEvent
import net.bestia.zone.ecs.battle.Damage
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Reacts to pending [Damage] against entities carrying an `ON_DAMAGE_TAKEN` [StatusEffect.TriggerEffect]
 * (e.g. reflect) before [net.bestia.zone.ecs.battle.ReceivedDamageSystem] (`@Order(50)`) applies it
 * to [net.bestia.zone.ecs.battle.status.Health]. Ordered at 45 so this runs first in the same tick.
 *
 * Works per [Damage.DamageAmount] entry, not the aggregate total, so simultaneous hits from
 * multiple attackers attribute correctly. Only evaluates non-reflected amounts (see
 * [Damage.DamageAmount.isReflected]) to stop a pair of reflect effects bouncing damage forever.
 *
 * Known, deliberate limitation: if an action schedules new [Damage] on an entity that doesn't
 * already have one this tick (e.g. reflecting onto an attacker), `World.add` defers that to after
 * this tick's system phase finishes (see `World.kt`'s `iterating` guard), so the reflected damage
 * is only applied next tick, not later in this one. Acceptable at the default tick rate.
 */
@SpringComponent
@Order(45)
class StatusEffectDamageInterceptSystem(
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry
) : System {

  override val reads: ComponentClassSet = setOf(Damage::class, StatusEffects::class)
  override val writes: ComponentClassSet = setOf(Damage::class, StatusEffects::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Damage::class, StatusEffects::class).each { id ->
      val damage = get<Damage>()
      val effects = get<StatusEffects>()

      if (effects.activeEffects.isEmpty() || damage.amounts.isEmpty()) return@each

      val resultAmounts = mutableListOf<Damage.DamageAmount>()

      for (incoming in damage.amounts) {
        if (incoming.isReflected) {
          resultAmounts.add(incoming)
          continue
        }

        var remaining = incoming
        val toConsume = mutableListOf<Long>()

        for (active in effects.activeEffects.toList()) {
          val definition = statusEffectDefinitionRegistry.findById(active.definitionId) ?: continue
          for (effect in definition.effects) {
            if (effect !is StatusEffect.TriggerEffect || effect.on != StatusEffectTriggerEvent.ON_DAMAGE_TAKEN) continue

            val mitigated = effect.action.apply(world, active, id, remaining)
            remaining = remaining.copy(amount = mitigated)

            if (effect.consumeOnTrigger) {
              toConsume.add(active.instanceId)
            }
          }
        }

        // consume() marks the StatusEffects component dirty itself when it removes an instance.
        toConsume.forEach { instanceId -> effects.consume(instanceId) }

        if (remaining.amount > 0) {
          resultAmounts.add(remaining)
        }
      }

      damage.amounts.clear()
      damage.amounts.addAll(resultAmounts)
    }
  }
}
