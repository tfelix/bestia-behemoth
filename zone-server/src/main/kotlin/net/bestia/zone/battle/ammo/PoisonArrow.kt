package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A venom-soaked head: a landed shot may poison, against the target's usual poison resistance. */
@Component
class PoisonArrow(private val statusEffects: StatusEffectService) : AmmoEffect {

  override fun onHit(world: World, attackerId: EntityId, targetId: EntityId, damage: Damage) {
    statusEffects.tryInflict(world, targetId, StatusEffectId.POISON, 1, attackerId, CHANCE_PERCENT)
  }

  companion object {
    const val CHANCE_PERCENT = 20
  }
}
