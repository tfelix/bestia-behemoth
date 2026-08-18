package net.bestia.zone.battle.skill.scripts

import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.damage.SurveyResult
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.cartography.CartographyConfig
import org.springframework.stereotype.Component

/**
 * Charts the land around a point: the only way a new chart comes into the world.
 *
 * Aimed at ground rather than at anything, so an entity-targeted context is refused outright - surveying a
 * wolf is not a coherent request, and the skill's `targetType: GROUND` in `skills.yml` means a real client
 * never sends one.
 *
 * ### What it does not check
 *
 * Whether the surveyor is holding a blank chart. A [SkillStrategy] is a pure function of a [BattleContext] and
 * a battle context has no inventory in it, so the refusal happens where the chart is actually written -
 * `ChartService.mint`, with `CHART_NEEDS_BLANK`. The cost of that is a cast that channels and spends mana
 * before finding out; crafting resolves its materials at the same late point for the same reason.
 *
 * ### The radius ladder
 *
 * A plain multiple of the caster's rank, so rank 1 charts a kilometre and rank 5 - the cap in
 * `master_skill_tree.yml` - charts five. See [CartographyConfig.surveyRadiusPerLevelMetres]; the skill's
 * description promises exactly that each level makes surveying reach further.
 */
@Component
class Cartography(
  private val config: CartographyConfig
) : SkillStrategy {

  override fun isAttackPossible(ctx: BattleContext): Boolean = ctx is GroundBattleContext

  override fun doAttack(ctx: BattleContext): Damage =
    // Floored at rank 1: `KnownSkills.levelOf` answers 0 for a skill nobody has taken, and a zero radius
    // would throw out of `SurveyResult` in the middle of a system rather than refusing the cast.
    SurveyResult(radiusMetres = config.surveyRadiusPerLevelMetres * ctx.usedAttack.level.coerceAtLeast(1))
}
