package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.ammo.AmmoSupply
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.battle.gear.WeaponType
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Javelin (`skills.yml` id 173): hurls the spear or the thrown weapon in hand at a foe for `150% + 30%/lv` ATK, as a
 * shot. A spear comes back to hand; a thrown weapon spends one more of its kind from the bag, like an arrow.
 */
@Component
class Javelin(
  losService: LineOfSightService,
  private val ammo: AmmoSupply,
) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return when (world.get(casterId, CombatStats::class)?.values?.heldWeaponType) {
      WeaponType.SPEAR -> null
      WeaponType.THROWN -> if (ammo.hasThrownSpare(world, casterId)) null else OpError.SKILL_NOTHING_TO_THROW
      else -> OpError.SKILL_NEEDS_SPEAR_OR_THROWN
    }
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) &&
        ctx.world.heldWeaponTypeOf(ctx.casterId) in THROWABLE && ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val thrown = ctx.world.throwHeldWeapon()
    if (thrown == null) {
      // The last spare left the bag between the button and now.
      ctx.world.tellCaster(OpError.SKILL_NOTHING_TO_THROW)
      return null
    }

    val shot = ctx.battle.usedAttack.copy(attackType = AttackType.RANGED_PHYSICAL, ammunition = thrown)
    ctx.world.strike(target, shot, atkPercent(ctx.skillLevel))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5

    private val THROWABLE = setOf(WeaponType.SPEAR, WeaponType.THROWN)

    fun atkPercent(level: Int): Int {
      return 150 + 30 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
