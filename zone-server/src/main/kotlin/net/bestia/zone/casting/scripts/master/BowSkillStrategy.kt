package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.attack.BattleAttack
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * A Hunter's skill shot from the bow in hand. It reaches as far as the bow, Vulture's Eye included. Without a bow or
 * an arrow for it the button refuses it; without a bow at resolution it fizzles. A cast takes one arrow, see [nockArrow].
 */
abstract class BowSkillStrategy(
  losService: LineOfSightService,
  private val ammo: AmmoSupply,
) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val combat = world.get(casterId, CombatStats::class)?.values ?: return OpError.SKILL_NEEDS_BOW
    val bow = combat.weapon.ranged?.takeIf { combat.heldWeaponType == WeaponType.BOW } ?: return OpError.SKILL_NEEDS_BOW

    return if (ammo.nextFor(world, casterId, bow) == null) OpError.ATTACK_OUT_OF_AMMO else null
  }

  override fun reachOf(world: World, casterId: EntityId, cost: SkillLevelCost): Long {
    return world.get(casterId, CombatStats::class)?.values?.weapon?.ranged?.range ?: cost.rangeTiles
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return super.isCastPossible(ctx) && ctx.world.heldWeaponTypeOf(ctx.casterId) == WeaponType.BOW
  }

  /** Takes one arrow from the quiver as the shot this cast flies; null, and the caster told, when none is left. */
  protected fun nockArrow(ctx: SkillContext): BattleAttack? {
    val arrow = ctx.world.drawAmmo()
    if (arrow == null) {
      ctx.world.tellCaster(OpError.ATTACK_OUT_OF_AMMO)
      return null
    }

    return ctx.battle.usedAttack.copy(
      attackType = AttackType.RANGED_PHYSICAL,
      attackElement = arrow.element,
      ammunition = arrow
    )
  }

  protected fun Damage?.landed(): Boolean {
    return this != null && this != Miss && this != Blocked
  }
}
