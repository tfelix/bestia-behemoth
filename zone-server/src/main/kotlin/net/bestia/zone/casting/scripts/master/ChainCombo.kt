package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.attack.TripleBlow
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Chain Combo (`skills.yml` id 145): right after a Triple Blow, four weapon blows on a foe that share
 * `250% + 50%/lv` ATK. Each Triple Blow allows one.
 */
@Component
class ChainCombo(
  losService: LineOfSightService,
  private val tripleBlow: TripleBlow,
) : BasicMagicSkillStrategy(losService) {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return if (tripleBlow.mayFollowUp(world, casterId)) null else OpError.SKILL_NEEDS_TRIPLE_BLOW
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target) &&
        ctx.world.followsTripleBlow()
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    if (!ctx.world.spendTripleBlow()) return null

    for (percent in hitPercents(ctx.skillLevel)) {
      ctx.world.strike(target, ctx.battle.usedAttack, percent)
    }

    return null
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val HITS = 4

    fun atkPercent(level: Int): Int {
      return 250 + 50 * level.coerceIn(1, MAX_LEVEL)
    }

    /** [atkPercent] split over the [HITS], rounded so the blows still add up to all of it. */
    fun hitPercents(level: Int): List<Int> {
      val total = atkPercent(level)

      return (0 until HITS).map { hit -> total * (hit + 1) / HITS - total * hit / HITS }
    }
  }
}
