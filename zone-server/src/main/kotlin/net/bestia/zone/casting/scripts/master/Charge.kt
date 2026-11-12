package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.EntityBattleContext
import net.bestia.zone.battle.LineOfSightService
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.BasicMagicSkillStrategy
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component
import kotlin.math.abs
import kotlin.math.max

/**
 * Charge (`skills.yml` id 138): the Knight dashes next to a foe, strikes it at 150% ATK and provokes it for 3 s.
 * Ground it cannot step onto stops the dash short; then nothing is struck. Cools down `15 s - 0.5 s/lv`.
 */
@Component
class Charge(losService: LineOfSightService) : BasicMagicSkillStrategy(losService) {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    return SkillLevelCost.of(skill).copy(cooldownSeconds = cooldownSeconds(level))
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    val target = ctx.targetEntityId ?: return false

    return ctx.battle is EntityBattleContext && super.isCastPossible(ctx) && ctx.world.isFoe(target)
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = ctx.targetEntityId ?: return null
    val targetAt = ctx.world.positionOf(target) ?: return null

    ctx.world.forceMove(ctx.casterId, targetAt, ctx.battle.usedAttack.range.toInt(), keepDistance = 1)
    val knightAt = ctx.world.positionOf(ctx.casterId) ?: return null
    if (!isNextTo(knightAt, targetAt)) return null

    ctx.world.strike(target, ctx.battle.usedAttack, IMPACT_ATK_PERCENT)
    Provoke.provoke(ctx.world, target, level = 1, durationSeconds = PROVOKE_SECONDS)

    return null
  }

  private fun isNextTo(a: Vec3L, b: Vec3L): Boolean {
    return max(abs(a.x - b.x), abs(a.y - b.y)) <= 1
  }

  companion object {
    private const val MAX_LEVEL = 5
    const val IMPACT_ATK_PERCENT = 150
    const val PROVOKE_SECONDS = 3.0

    fun cooldownSeconds(level: Int): Float {
      return 15f - 0.5f * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
