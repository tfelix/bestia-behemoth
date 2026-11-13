package net.bestia.zone.casting.scripts.master

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.ScryTarget
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.identity.ecs.Master
import net.bestia.zone.identity.ecs.PartyMembership
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Scry (`skills.yml` id 63): the Sage sees a far place for 20 s. `args[0]` picks what it looks at:
 * - [SPOT]: the aimed-at spot on the map, which must be charted.
 * - [PARTY_MEMBER]: where the party member whose master id is `args[1]` stands when the cast ends.
 *
 * Casting it again while the sight is open is the way back to the body.
 */
@Component
class Scry : SkillStrategy {

  override val toggledStatus: StatusEffectId = StatusEffectId.SCRYING

  // Resolving it must not count as the body acting, or the sight it just opened would end on the spot.
  override val isReportedAsAction: Boolean = false

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return when (args.firstOrNull() ?: SPOT) {
      SPOT -> null
      PARTY_MEMBER -> if (isPartyMember(world, casterId, args.getOrNull(1))) null else OpError.SCRY_NO_SIGHT
      else -> OpError.SKILL_INVALID_CHOICE
    }
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return targetOf(ctx) != null
  }

  override fun execute(ctx: SkillContext): Damage? {
    val target = targetOf(ctx) ?: return null
    ctx.world.scry(target, viewRadiusMetres(ctx.skillLevel))

    return null
  }

  private fun targetOf(ctx: SkillContext): ScryTarget? {
    return when (ctx.args.firstOrNull() ?: SPOT) {
      SPOT -> ctx.targetPosition?.let { ScryTarget.Spot(it) }
      PARTY_MEMBER -> ctx.args.getOrNull(1)?.let { ScryTarget.PartyMember(it) }
      else -> null
    }
  }

  private fun isPartyMember(world: World, casterId: EntityId, masterId: Long?): Boolean {
    if (masterId == null || masterId == world.get(casterId, Master::class)?.masterId) return false

    return world.get(casterId, PartyMembership::class)?.members?.any { it.masterId == masterId } == true
  }

  companion object {
    const val SPOT = 0L
    const val PARTY_MEMBER = 1L

    private const val METRES_PER_LEVEL = 20.0
    private const val MAX_LEVEL = 5

    fun viewRadiusMetres(level: Int): Double {
      return METRES_PER_LEVEL * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
