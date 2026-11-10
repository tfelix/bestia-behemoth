package net.bestia.zone.casting

import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A weapon blow a skill lands on one target among many - a sweep, a brandished spear, an arrow shower - with the
 * swing's own formula, hit roll and crit, scaled by the skill's power. A single-target strike already has its
 * target's context in [SkillContext.battle]; an area skill asks this once per target.
 */
@Component
class SkillStrikes(
  private val battleContexts: BattleContextFactory,
  private val attackStrategies: AttackStrategyFactory,
  private val ammo: AmmoSupply,
) {

  /** The blow [casterId] deals [targetId] at [powerPercent] of a swing, or null when there is no fight to resolve. */
  fun strike(world: World, casterId: EntityId, targetId: EntityId, attack: BattleAttack, powerPercent: Int): Damage? {
    val battle = battleContexts.create(world, casterId, attack, targetId, null) as? EntityBattleContext ?: return null
    battle.damageVariables.attackPhysicalMod = powerPercent / 100f

    return attackStrategies.getAttackStrategy(battle).execute(battle)
  }

  /** Takes one shot for the held bow or crossbow out of the quiver; null when none is left or nothing shoots. */
  fun drawAmmo(world: World, casterId: EntityId): Ammunition? {
    val weapon = world.get(casterId, CombatStats::class)?.values?.weapon?.ranged ?: return null
    val next = ammo.nextFor(world, casterId, weapon) ?: return null

    return next.takeIf { ammo.draw(world, casterId, it) }
  }
}
