package net.bestia.zone.stronghold

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.prop.PropInteraction
import net.bestia.zone.script.ScriptArgKeys
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** A click on a seed's sprout asks what planting costs; the click the client sends once confirmed plants. */
@Component
class EmpyriteSeedInteraction(
  private val founding: StrongholdFounding,
) : PropInteraction {

  override val kind = StaticEntityKind.EMPYRITE_SEED

  override fun interact(world: World, actorId: EntityId, propId: EntityId): OpError? {
    return founding.offer(world, actorId, propId)
  }

  override fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    if (args.text(ScriptArgKeys.ACTION) == PLANT) {
      return founding.plant(world, actorId, propId)
    }

    return interact(world, actorId, propId)
  }

  companion object {
    const val PLANT = "plant"
  }
}
