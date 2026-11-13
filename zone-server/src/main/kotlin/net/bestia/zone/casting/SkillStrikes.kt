package net.bestia.zone.casting

import net.bestia.zone.battle.BattleContextFactory
import net.bestia.zone.battle.Element
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.Weapon
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.ammo.Ammunition
import net.bestia.zone.battle.attack.AttackStrategyFactory
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.ecs.core.World
import net.bestia.zone.item.ecs.Equipment
import net.bestia.zone.item.equip.EquipmentSlot
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

  /**
   * The blow [casterId] deals [targetId] at [powerPercent] of a swing, with the second weapon if [offHand]. Null when
   * there is no fight to resolve, or no second weapon.
   */
  fun strike(
    world: World,
    casterId: EntityId,
    targetId: EntityId,
    attack: BattleAttack,
    powerPercent: Int,
    offHand: Boolean = false,
  ): Damage? {
    val battle = battleContexts.create(world, casterId, attack, targetId, null) as? EntityBattleContext ?: return null
    battle.damageVariables.attackPhysicalMod = powerPercent / 100f
    val blow = if (offHand) battle.withOffHand(offHandOf(world, casterId) ?: return null, FULL_POWER) else battle

    return attackStrategies.getAttackStrategy(blow).execute(blow)
  }

  private fun offHandOf(world: World, casterId: EntityId): Weapon? {
    return world.get(casterId, CombatStats::class)?.values?.offHandWeapon
  }

  private companion object {
    /** A skill's blow with the second weapon hits as hard as with the first: the skill sets the power. */
    const val FULL_POWER = 100
  }

  /**
   * What [casterId] throws: the spear in hand, which comes back to it, or one more of the thrown weapon in hand from
   * the bag. Null when it holds neither, or has nothing left to throw.
   */
  fun throwHeldWeapon(world: World, casterId: EntityId): Ammunition? {
    val combat = world.get(casterId, CombatStats::class)?.values ?: return null
    if (combat.heldWeaponType == WeaponType.THROWN) return ammo.drawThrown(world, casterId)
    if (combat.heldWeaponType != WeaponType.SPEAR) return null

    val spear = world.get(casterId, Equipment::class)?.get(EquipmentSlot.RIGHT_HAND) ?: return null
    return Ammunition(itemId = spear.itemId, attack = 0, element = Element.NORMAL)
  }

  /** Takes one shot for the held bow or crossbow out of the quiver; null when none is left or nothing shoots. */
  fun drawAmmo(world: World, casterId: EntityId): Ammunition? {
    val weapon = world.get(casterId, CombatStats::class)?.values?.weapon?.ranged ?: return null
    val next = ammo.nextFor(world, casterId, weapon) ?: return null

    return next.takeIf { ammo.draw(world, casterId, it) }
  }
}
