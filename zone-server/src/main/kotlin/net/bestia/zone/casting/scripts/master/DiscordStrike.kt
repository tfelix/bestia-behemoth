package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.AttackType
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Discord Strike (`skills.yml` id 163): with an instrument, a sound wave at a foe for `150% + 30%/lv` ATK. It flies
 * like a shot, so it rolls DEX as a bow does and a Pneuma stops it.
 */
@Component
class DiscordStrike(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return BardWeapon.INSTRUMENT.refusalFor(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && BardWeapon.INSTRUMENT.isHeld(ctx) &&
        ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val soundWave = ctx.battle.usedAttack.copy(attackType = AttackType.RANGED_PHYSICAL)
    ctx.world.strike(target, soundWave, atkPercent(ctx.skillLevel))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5

    fun atkPercent(level: Int): Int {
      return 150 + 30 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
