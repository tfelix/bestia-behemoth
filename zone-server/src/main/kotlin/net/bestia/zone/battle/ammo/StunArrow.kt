package net.bestia.zone.battle.ammo

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A lodestone head that rings the skull it strikes: a landed shot may stun. */
@Component
class StunArrow(private val statusEffects: StatusEffectService) : AmmoEffect {

  override fun onHit(world: World, attackerId: EntityId, targetId: EntityId, damage: Damage) {
    statusEffects.tryInflict(world, targetId, StatusEffectId.STUN, 1, attackerId, CHANCE_PERCENT)
  }

  companion object {
    const val CHANCE_PERCENT = 10
  }
}
