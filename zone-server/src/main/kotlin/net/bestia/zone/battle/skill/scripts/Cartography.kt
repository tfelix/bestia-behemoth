package net.bestia.zone.battle.skill.scripts

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.BattleContext
import net.bestia.zone.battle.GroundBattleContext
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.skill.SkillStrategy
import net.bestia.zone.cartography.CartographyConfig
import net.bestia.zone.cartography.SurveyService
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.Master
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.movement.Position
import org.springframework.stereotype.Component

/**
 * Charts the land around a point: the only way a new chart comes into the world.
 */
@Component
class Cartography(
  private val config: CartographyConfig,
  private val surveyService: SurveyService,
  private val world: World
) : SkillStrategy {

  override fun isAttackPossible(ctx: BattleContext): Boolean = ctx is GroundBattleContext

  override fun execute(ctx: BattleContext): Damage? {
    val groundBattleContext = BattleContext.verifyGroundBattleContext(ctx)

    // TODO verify the bestia has a paper in its inventory
    // TODO implement this minigame and failure conditions

    // Floored at rank 1: `KnownSkills.levelOf` answers 0 for a skill nobody has taken, and a zero radius
    // would throw out of `SurveyResult` in the middle of a system rather than refusing the cast.
    val surveyRadius = config.surveyRadiusPerLevelMetres * ctx.usedAttack.level.coerceAtLeast(1)

    applySurvey(groundBattleContext, surveyRadius)

    return null
  }


  /**
   * Sends the survey off to be charted, and does no database work here.
   *
   * Everything a chart needs - the blank to consume, the instance to mint, the row to write - is relational, and
   * this runs under the world lock on `zone-tick` where that is forbidden. So the only work done here is
   * resolving the three ids off the live world, which is the part that *cannot* be done later: by the time an
   * async job runs, the caster may have logged out.
   */
  private fun applySurvey(ctx: GroundBattleContext, radius: Double) {
    val at = world.get(ctx.attacker.id, Position::class)?.toVec3L()

    if (at == null) {
      LOG.debug { "Survey by ${ctx.attacker.id} has no position to centre on" }
      return
    }

    val masterId = world.get(ctx.attacker.id, Master::class)?.masterId
    if (masterId == null) {
      LOG.debug { "Entity ${ctx.attacker.id}  is not a master and cannot hold a chart" }
      return
    }

    surveyService.survey(
      world = world,
      masterId = masterId,
      accountId = world.get(casterId, Account::class)?.accountId,
      entityId = casterId,
      centre = at,
      radiusMetres = result.radiusMetres
    )
  }

  companion object {
    private val lOG = KotlinLogging.logger { }
  }
}
