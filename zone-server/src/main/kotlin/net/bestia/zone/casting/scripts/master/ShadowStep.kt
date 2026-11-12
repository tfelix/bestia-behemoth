package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Shadow Step (`skills.yml` id 131): from Hide or Cloak, the caster blinks onto the tile behind a foe up to
 * `2 m + 2 m/lv` away and stays hidden, so a Backstab can follow.
 */
@Component
class ShadowStep(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override val isReportedAsAction: Boolean = false

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    return SkillLevelCost.of(skill).copy(rangeTiles = rangeTiles(level))
  }

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return FromHiding.refusal(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && FromHiding.isHidden(ctx)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val behindTarget = ctx.world.spotBehind(target) ?: return null
    ctx.world.blink(ctx.casterId, behindTarget)

    return null
  }

  companion object {
    private const val MAX_LEVEL = 3

    fun rangeTiles(level: Int): Long {
      return 2L + 2L * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
