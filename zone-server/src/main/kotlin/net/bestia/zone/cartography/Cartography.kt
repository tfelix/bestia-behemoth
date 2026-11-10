package net.bestia.zone.cartography

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.cartography.survey.SurveyTier
import net.bestia.zone.cartography.survey.TheodoliteSurveys
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Starts a survey at the caster's own theodolite: the cast spends paper and ink (the skill's reagents) and opens
 * the needle game. `args[0]` is the survey size in kilometres, which the skill level unlocks. See
 * [TheodoliteSurveys] for the game, the work and the chart.
 */
@Component
class Cartography(
  private val surveys: TheodoliteSurveys,
) : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    val tier = SurveyTier.ofKilometres(args.firstOrNull()) ?: return OpError.SKILL_INVALID_CHOICE
    if (skillLevel < tier.minLevel) return OpError.SURVEY_TIER_LOCKED

    val masterId = world.get(casterId, Master::class)?.masterId ?: return OpError.SURVEY_NO_THEODOLITE
    val at = world.get(casterId, Position::class)?.toVec3L() ?: return OpError.SURVEY_NO_THEODOLITE

    return if (surveys.idleTheodoliteNear(masterId, at) == null) OpError.SURVEY_NO_THEODOLITE else null
  }

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    val tier = SurveyTier.ofKilometres(ctx.args.firstOrNull()) ?: return null
    val masterId = ctx.world.masterIdOf(ctx.casterId) ?: return null
    val accountId = ctx.world.accountIdOf(ctx.casterId) ?: return null
    val at = ctx.world.positionOf(ctx.casterId) ?: return null
    // Checked again: the theodolite may have been taken down, or started by another cast, during the bar.
    val theodolite = surveys.idleTheodoliteNear(masterId, at) ?: return null

    surveys.open(masterId, accountId, theodolite, tier, ctx.skillLevel)

    return null
  }
}
