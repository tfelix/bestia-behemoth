package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillContext
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.cartography.CartographyConfig
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Charts the land around a point: the only way a new chart comes into the world.
 *
 * No range or line-of-sight gate - a survey is of the ground the surveyor is standing on, and the aimed-at
 * point only decides where the disc is centred.
 *
 * [CartographyConfig] and [SurveyService] are injected directly rather than reached through the context:
 * neither needs a world of its own - the one method here that touches the world is handed it - so neither
 * closes the cycle a `World` field would. The *resolving* half still goes through [SkillContext.world],
 * because that is where the cast's budget lives.
 */
@Component
class Cartography(
  private val config: CartographyConfig,
  private val surveyService: SurveyService,
) : SkillStrategy {

  /**
   * A survey needs a sheet of blank vellum, and channels for five seconds before it uses one.
   *
   * Checked here so those five seconds are not spent on a survey that was never going to produce anything,
   * and checked again - and only then taken - by `ChartService.mint`, since the sheet counted here can be
   * dropped or traded away while the bar is still filling.
   */
  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int): OpError? =
    surveyService.checkBlank(world, casterId)

  override fun isCastPossible(ctx: SkillContext): Boolean = ctx.battle is GroundBattleContext

  override fun execute(ctx: SkillContext): Damage? {
    ctx.requireGroundContext()

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
