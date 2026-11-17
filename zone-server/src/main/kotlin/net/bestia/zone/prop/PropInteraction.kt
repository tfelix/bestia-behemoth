package net.bestia.zone.prop

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.StaticEntityKind
import net.bestia.zone.script.ScriptArgs
import net.bestia.zone.util.EntityId

/**
 * What clicking a static prop of [kind] does. A slice that gives a building a purpose implements this, so the
 * click handler does not have to know every such slice.
 */
interface PropInteraction {

  val kind: StaticEntityKind

  /** @return the refusal to send back, or null when the click was acted on. */
  fun interact(world: World, actorId: EntityId, propId: EntityId): OpError?

  /** A click that carries what the player chose first, such as a quest to sign. Most props need none. */
  fun interact(world: World, actorId: EntityId, propId: EntityId, args: ScriptArgs): OpError? {
    return interact(world, actorId, propId)
  }
}
