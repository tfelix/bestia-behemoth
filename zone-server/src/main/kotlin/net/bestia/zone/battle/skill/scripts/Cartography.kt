package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.cartography.CartographyConfig
import org.springframework.stereotype.Component

/**
 * Charts the land around a point: the only way a new chart comes into the world.
 *
 * No range or line-of-sight gate - a survey is of the ground the surveyor is standing on, and the aimed-at
 * point only decides where the disc is centred.
 *
 * [CartographyConfig] is injected directly rather than reached through the context: it needs no world, so it
 * closes no cycle. Only a service whose methods take a world has to go through [SkillContext.world].
 */
@Component
class Cartography(
  private val config: CartographyConfig,
) : SkillStrategy {

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is GroundBattleContext

  override fun execute(ctx: SkillContext): Damage? {
    ctx.requireGroundContext()

    // TODO verify the bestia has a paper in its inventory
    // TODO implement this minigame and failure conditions

    val masterId = ctx.world.masterIdOf(ctx.casterId)
    if (masterId == null) {
      LOG.debug { "Entity ${ctx.casterId} is not a master and cannot hold a chart" }
      return null
    }

    // The aimed-at point, falling back to where the surveyor stands. `skills.yml` catalogues CARTOGRAPHY as
    // `targetType: GROUND` with a range, so the client lets the player pick where to chart and sends it -
    // centring on the caster regardless would silently throw that choice away.
    val centre = ctx.targetPosition ?: ctx.world.positionOf(ctx.casterId)
    if (centre == null) {
      LOG.debug { "Survey by ${ctx.casterId} has no position to centre on" }
      return null
    }

    // Floored at rank 1: `KnownSkills.levelOf` answers 0 for a skill nobody has taken, and a zero radius
    // would be refused by SurveyService rather than charting anything.
    val radiusMetres = config.surveyRadiusPerLevelMetres * ctx.skillLevel.coerceAtLeast(1)

    ctx.world.survey(
      masterId = masterId,
      accountId = ctx.world.accountIdOf(ctx.casterId),
      centre = centre,
      radiusMetres = radiusMetres
    )

    return null
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
