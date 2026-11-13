package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Blocked
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.Miss
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Hobbling Lash (`skills.yml` id 162): with a whip, a weapon blow at `120% + 20%/lv` ATK on a foe. A blow that lands
 * leaves it Slowed by 20% for `3 s + 1 s/lv`.
 */
@Component
class HobblingLash(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return BardWeapon.WHIP.refusalFor(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && BardWeapon.WHIP.isHeld(ctx) &&
        ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val blow = ctx.world.strike(target, ctx.battle.usedAttack, atkPercent(ctx.skillLevel)) ?: return null
    if (blow == Miss || blow == Blocked) return null

    ctx.world.applyStatusEffect(
      target, StatusEffectId.SLOWED.id, level = SLOWED_LEVEL, durationSeconds = slowedSeconds(ctx.skillLevel)
    )

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5

    /** Slowed takes 10% of the speed per level. */
    const val SLOWED_LEVEL = 2

    fun atkPercent(level: Int): Int {
      return 120 + 20 * level.coerceIn(1, MAX_LEVEL)
    }

    fun slowedSeconds(level: Int): Double {
      return 3.0 + level.coerceIn(1, MAX_LEVEL)
    }
  }
}
