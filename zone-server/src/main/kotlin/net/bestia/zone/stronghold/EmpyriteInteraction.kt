package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * A member's click on their guild's Empyrite opens the Stronghold window. Its Invest buttons click with a track, its
 * Hire button with the steward action.
 */
@Component
class EmpyriteInteraction(
  private val investment: StrongholdInvestment,
) : PropInteraction {

  override val kind = StaticEntityKind.EMPYRITE

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return investment.open(world, actorId, propId)
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    return when (args.text(ScriptArgKeys.ACTION)) {
      INVEST -> invest(world, actorId, propId, args)
      HIRE_STEWARD -> investment.hireSteward(world, actorId, propId)
      else -> interact(world, actorId, propId)
    }
  }

  private fun invest(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    val track = args.text(ScriptArgKeys.TRACK)?.let { name -> StrongholdTrack.entries.firstOrNull { it.name == name } }
      ?: return OpError.REQUEST_REFUSED

    return investment.invest(world, actorId, propId, track)
  }

  companion object {
    const val INVEST = "invest"
    const val HIRE_STEWARD = "hire_steward"
  }
}
