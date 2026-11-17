package net.bestia.zone.script

import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.script.ecs.ScriptComponent
import net.bestia.zone.util.EntityId

/**
 * What an entity with a [ScriptComponent] does when a player clicks it. A Spring bean; the component names it by
 * its simple class name.
 */
interface EntityScript {

  /** Runs inside the tick's world scope. @return the refusal to send back, or null. */
  fun onInteract(world: World, actorId: EntityId, entityId: EntityId): OpError?
}
