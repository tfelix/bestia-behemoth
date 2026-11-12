package net.bestia.zone.battle.attack

import net.bestia.zone.battle.StatusEffectService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** What the attacker's gear may put on a target with every swing that lands, like a stun from a heavy mace. */
@Component
class GearSwingStatuses(private val statusEffects: StatusEffectService) : SwingProc {

  override fun onSwingLanded(world: World, attackerId: EntityId, targetId: EntityId, swing: Damage) {
    val chances = world.get(attackerId, CombatStats::class)?.values?.swingStatusChance ?: return

    for ((effect, chancePercent) in chances) {
      statusEffects.tryInflict(world, targetId, effect, level = 1, sourceEntityId = attackerId, chancePercent = chancePercent)
    }
  }
}
