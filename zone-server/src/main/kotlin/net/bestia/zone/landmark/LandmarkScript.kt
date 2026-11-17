package net.bestia.zone.landmark

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * What a landmark does. A Spring bean, bound to a landmark kind by its simple class name in the `landmark.script`
 * field of `prop-kinds.yml`.
 */
interface LandmarkScript {

  /** A player clicked the landmark. Runs inside the tick's world scope. */
  fun onInteract(world: World, actorId: EntityId, landmarkId: EntityId): OpError?

  /** The landmark's HP reached zero. It is removed right after and never raised again on this world. */
  fun onDestroyed(world: World, landmarkId: EntityId) {
  }
}
