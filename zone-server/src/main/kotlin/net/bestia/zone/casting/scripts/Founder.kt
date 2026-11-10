package net.bestia.zone.casting.scripts

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.ecs.core.World
import net.bestia.zone.settlement.TownService
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * The Founder skill (`skills.yml` id 55): plants a city sign site where the caster stands, under the name the
 * caster chose with `PlanSettlementCMSG` before casting. The aimed point is ignored, so there is no range to check.
 */
@Component
class Founder(
  private val towns: TownService,
) : SkillStrategy {

  override fun checkCastStart(world: World, casterId: EntityId, skillLevel: Int, args: List<Long>): OpError? {
    return towns.foundingRefusal(world, casterId)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.foundTown()

    return null
  }
}
